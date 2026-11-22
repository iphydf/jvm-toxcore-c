package chat.tox.ktox

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ExifInterface
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import chat.tox.ktox.ui.Navigator
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.min

/** The process's Tox profiles, which live as long as the process and not just the activity. */
class KTox :
    Application(),
    Platform {
    lateinit var profiles: Profiles
        private set

    /** Here rather than in the activity, so turning the phone keeps the page. */
    val navigator = Navigator()

    private val notifications get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannels(
            listOf(
                NotificationChannel(MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(REQUESTS, "Friend requests", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CONNECTED, "Staying connected", NotificationManager.IMPORTANCE_MIN),
                NotificationChannel(CALLS, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build(),
                    )
                    enableVibration(true)
                },
                NotificationChannel(ONGOING_CALLS, "Ongoing calls", NotificationManager.IMPORTANCE_LOW),
            ),
        )
        profiles = Profiles(filesDir, this)
        profiles.start()
    }

    /** The activity's file picker while it's there: pickers belong to activities. */
    var picker: ((images: Boolean, picked: (PickedFile) -> Unit) -> Unit)? = null

    override fun pickFile(
        images: Boolean,
        picked: (PickedFile) -> Unit,
    ) {
        picker?.invoke(images, picked)
    }

    override fun open(
        file: File,
        name: String,
    ) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.').lowercase()) ?: "*/*"
        val view =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(Intent.createChooser(view, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // Nothing opens it: Save still puts it in Downloads.
        }
    }

    override fun saveToDownloads(
        file: File,
        name: String,
    ): String? {
        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return null
            contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            "Downloads"
        } catch (_: IOException) {
            contentResolver.delete(uri, null, null)
            null
        }
    }

    override fun decodeImage(
        file: File,
        size: Int,
    ): ImageBitmap? = decode(size) { BitmapFactory.decodeFile(file.path, it) }?.let { rotate(it, file) }?.asImageBitmap()

    override fun avatarPng(
        image: ByteArray,
        size: Int,
    ): ByteArray? {
        val bitmap = decode(size) { BitmapFactory.decodeByteArray(image, 0, image.size, it) } ?: return null
        val side = min(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, min(side, size), min(side, size), true)
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /** Decodes at the smallest power-of-two scale that is still at least [size] across. */
    private fun decode(
        size: Int,
        decode: (BitmapFactory.Options) -> Bitmap?,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        return decode(BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Turns a camera photo the way it was taken. */
    private fun rotate(
        bitmap: Bitmap,
        file: File,
    ): Bitmap {
        val degrees =
            when (runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0) }.getOrDefault(0)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> return bitmap
            }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    }

    override fun videoImages(): VideoImages = AndroidVideoImages()

    override fun log(message: String) {
        Log.i("kTox", message)
    }

    override fun openSound(
        recorded: (ShortArray) -> Unit,
        playout: Playout,
    ): CallSound = AndroidSound(this, recorded, playout)

    override val cameras by lazy { AndroidCamera.facings(this).size }

    override fun openCamera(
        front: Boolean,
        frame: (VideoFrame) -> Unit,
    ): AutoCloseable? = AndroidCamera.open(this, front, frame)

    override val hasEarpiece by lazy {
        getSystemService(AudioManager::class.java)
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
    }

    /** The activity's way to ask for the microphone and camera while it's there. */
    var callAsker: ((video: Boolean, then: () -> Unit) -> Unit)? = null

    override fun askForCall(
        video: Boolean,
        then: () -> Unit,
    ) {
        callAsker?.invoke(video, then) ?: then()
    }

    /** The call, for the service's notification. Set on the Tox thread. */
    @Volatile var call: Call? = null
        private set

    @Volatile var callFriend: Friend? = null
        private set

    private val proximity by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "ktox:call")
    }

    override fun callChanged(
        profile: ProfileId,
        call: Call?,
        friend: Friend?,
    ) {
        val was = this.call
        this.call = call
        callFriend = friend
        if (call != null && !call.outgoing && call.ringing) {
            if (was == null) notifyIncomingCall(call, friend)
        } else {
            notifications.cancel(CALL_ID)
            // The service shows the call, and keeps the microphone and camera going in the background.
            if (call != null || was != null) updateService()
        }
        // The screen goes off at the ear.
        val atEar = call != null && (call.outgoing || call.answered) && !call.speaker && !call.video
        if (atEar && !proximity.isHeld) {
            proximity.acquire(MAX_CALL_MS)
        } else if (!atEar && proximity.isHeld) {
            proximity.release()
        }
    }

    private val main by lazy { Handler(mainLooper) }

    private fun updateService() =
        main.post {
            val service = ToxService.running
            if (service != null) {
                service.update()
            } else {
                try {
                    startForegroundService(Intent(this, ToxService::class.java))
                } catch (e: IllegalStateException) {
                    Log.w("kTox", "can't keep the call going in the background", e)
                }
            }
        }

    private fun notifyIncomingCall(
        call: Call,
        friend: Friend?,
    ) {
        val name = friend?.displayName ?: call.friend.hex.take(SHORT_KEY)
        val show = callIntent(answer = null)
        val builder =
            Notification
                .Builder(this, CALLS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(name)
                .setContentText(if (call.videoCall) "Video call" else "Call")
                .setCategory(Notification.CATEGORY_CALL)
                .setFullScreenIntent(show, true)
                .setContentIntent(show)
                .setOngoing(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setStyle(
                Notification.CallStyle.forIncomingCall(
                    Person.Builder().setName(name).build(),
                    hangUpIntent(),
                    callIntent(answer = call.videoCall),
                ),
            )
        } else {
            builder
                .addAction(Notification.Action.Builder(null, "Decline", hangUpIntent()).build())
                .addAction(Notification.Action.Builder(null, "Answer", callIntent(answer = call.videoCall)).build())
        }
        notifications.notify(CALL_ID, builder.build().apply { flags = flags or Notification.FLAG_INSISTENT })
    }

    /** Opens the call's page, answering it (with video or without) if [answer] is set. */
    fun callIntent(answer: Boolean?): PendingIntent =
        PendingIntent.getActivity(
            this,
            if (answer == null) CALL_ID else CALL_ID + 1,
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.CALL, true)
                .apply { if (answer != null) putExtra(MainActivity.ANSWER_VIDEO, answer) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun hangUpIntent(): PendingIntent =
        PendingIntent.getBroadcast(this, 0, Intent(this, CallReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)

    override val canStayConnected get() = true

    override var stayConnected: Boolean
        get() = getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(STAY_CONNECTED, true)
        set(on) {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(STAY_CONNECTED, on).apply()
            // A call keeps it until the call ends.
            if (!on && call == null) stopService(Intent(this, ToxService::class.java))
        }

    override fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Tox ID", text))
    }

    override fun notifyMessage(
        profile: ProfileId,
        friend: Friend,
        message: Message,
    ) {
        notifications.notify(
            tag(profile, friend.key.hex),
            MESSAGE_ID,
            Notification
                .Builder(this, MESSAGES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(friend.displayName)
                .setContentText(message.preview)
                .setStyle(Notification.BigTextStyle().bigText(message.preview))
                .setWhen(message.time)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(openIntent(profile, friend.key))
                .build(),
        )
    }

    override fun notifyRequest(
        profile: ProfileId,
        request: FriendRequest,
    ) {
        notifications.notify(
            tag(profile, request.key.hex),
            REQUEST_ID,
            Notification
                .Builder(this, REQUESTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Friend request")
                .setContentText(request.message.ifBlank { request.key.hex.take(SHORT_KEY) })
                .setCategory(Notification.CATEGORY_SOCIAL)
                .setAutoCancel(true)
                .setContentIntent(openIntent(profile, null))
                .build(),
        )
    }

    override fun notifyInvite(
        profile: ProfileId,
        friend: Friend,
        invite: GroupInvite,
    ) {
        notifications.notify(
            tag(profile, invite.data),
            REQUEST_ID,
            Notification
                .Builder(this, REQUESTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Group invite from ${friend.displayName}")
                .setContentText(invite.name)
                .setCategory(Notification.CATEGORY_SOCIAL)
                .setAutoCancel(true)
                .setContentIntent(openIntent(profile, null))
                .build(),
        )
    }

    override fun notifyGroupMessage(
        profile: ProfileId,
        group: Group,
        message: GroupMessage,
    ) {
        notifications.notify(
            tag(profile, group.id.hex),
            MESSAGE_ID,
            Notification
                .Builder(this, MESSAGES)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(group.displayName)
                .setContentText("${message.peerName}: ${message.text}")
                .setStyle(Notification.BigTextStyle().bigText("${message.peerName}: ${message.text}"))
                .setWhen(message.time)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(openGroupIntent(profile, group.id))
                .build(),
        )
    }

    override fun clearGroupNotifications(
        profile: ProfileId,
        group: GroupId,
    ) {
        notifications.cancel(tag(profile, group.hex), MESSAGE_ID)
    }

    override fun clearNotifications(
        profile: ProfileId,
        friend: FriendKey,
    ) {
        notifications.cancel(tag(profile, friend.hex), MESSAGE_ID)
    }

    override fun clearProfile(profile: ProfileId) {
        notifications.activeNotifications
            .filter { it.tag?.startsWith(tag(profile, "")) == true }
            .forEach { notifications.cancel(it.tag, it.id) }
    }

    /** A notification's tag: what it's about, in [profile]. */
    private fun tag(
        profile: ProfileId,
        about: String,
    ) = "${profile.name}:$about"

    /** Opens the app, in [profile] if given and at [friend]'s chat if given. */
    fun openIntent(
        profile: ProfileId?,
        friend: FriendKey?,
    ): PendingIntent =
        PendingIntent.getActivity(
            this,
            "${profile?.name}:${friend?.hex}".hashCode(),
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.PROFILE, profile?.name)
                .putExtra(MainActivity.FRIEND, friend?.hex),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Opens the app at [group]'s chat in [profile]. */
    fun openGroupIntent(
        profile: ProfileId,
        group: GroupId,
    ): PendingIntent =
        PendingIntent.getActivity(
            this,
            "${profile.name}:${group.hex}".hashCode(),
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.PROFILE, profile.name)
                .putExtra(MainActivity.GROUP, group.hex),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val MESSAGES = "messages"
        const val REQUESTS = "requests"
        const val CONNECTED = "connected"
        const val CALLS = "calls"
        const val ONGOING_CALLS = "ongoing_calls"
        const val CALL_ID = 3

        /** The proximity lock lets go by itself after this, in case a call never ends. */
        const val MAX_CALL_MS = 4 * 60 * 60 * 1000L
        const val MESSAGE_ID = 1
        const val REQUEST_ID = 2
        const val PREFERENCES = "ktox"
        const val STAY_CONNECTED = "stay_connected"

        fun of(context: Context) = context.applicationContext as KTox
    }
}
