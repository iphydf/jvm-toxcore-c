package chat.tox.ktox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import chat.tox.ktox.ui.App
import chat.tox.ktox.ui.Page

class MainActivity : ComponentActivity() {
    private val navigator get() = KTox.of(this).navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = KTox.of(this)
        if (app.stayConnected) startForegroundService(Intent(this, ToxService::class.java))
        app.picker = { images, picked ->
            onPicked = picked
            pick.launch(if (images) arrayOf("image/*") else arrayOf("*/*"))
        }
        app.callAsker = { video, then ->
            val wanted = listOf(Manifest.permission.RECORD_AUDIO) + if (video) listOf(Manifest.permission.CAMERA) else emptyList()
            val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isEmpty()) {
                then()
            } else {
                onAsked = then
                ask.launch(missing.toTypedArray())
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        openFrom(intent)
        setContent {
            BackHandler(navigator.canGoBack) { navigator.back() }
            // A call shows over the lock screen, and keeps the screen on.
            val inCall =
                app.profiles.line.profile
                    .collectAsState()
                    .value != null
            LaunchedEffect(inCall) {
                setShowWhenLocked(inCall)
                setTurnScreenOn(inCall)
                if (inCall) {
                    window.addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            // Android 12 and later have colours from the wallpaper.
            val colors =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    { dark: Boolean -> if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this) }
                } else {
                    null
                }
            App(app.profiles, app, navigator, colors)
        }
    }

    private var onPicked: ((PickedFile) -> Unit)? = null

    /** The call goes on whatever the user says: without what they refused. */
    private var onAsked: (() -> Unit)? = null

    private val ask =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            onAsked?.invoke()
            onAsked = null
        }

    private val pick =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val picked = onPicked
            onPicked = null
            if (uri != null && picked != null) picked(PickedFile(displayName(uri)) { contentResolver.openInputStream(uri)!! })
        }

    private fun displayName(uri: Uri): String =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "file"

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    /** A notification opens its profile at its chat, or the call, which its Answer button answers. */
    private fun openFrom(intent: Intent) {
        val profiles = KTox.of(this).profiles
        intent.getStringExtra(PROFILE)?.let {
            profiles.show(ProfileId(it))
            navigator.show(Page.Chats)
        }
        intent.getStringExtra(FRIEND)?.let { navigator.show(Page.Chat(FriendKey(it))) }
        intent.getStringExtra(GROUP)?.let { navigator.show(Page.Group(GroupId(it))) }
        if (intent.getBooleanExtra(CALL, false)) {
            profiles.caller?.let { profiles.show(it.id) }
            navigator.push(Page.Call)
        }
        if (intent.hasExtra(ANSWER_VIDEO)) {
            val video = intent.getBooleanExtra(ANSWER_VIDEO, false)
            // Only once: the activity gets the same intent again after turning.
            intent.removeExtra(ANSWER_VIDEO)
            val app = KTox.of(this)
            app.askForCall(video) { profiles.caller?.answer(video) }
        }
    }

    companion object {
        const val PROFILE = "profile"
        const val FRIEND = "friend"
        const val GROUP = "group"
        const val CALL = "call"
        const val ANSWER_VIDEO = "answer_video"
    }
}
