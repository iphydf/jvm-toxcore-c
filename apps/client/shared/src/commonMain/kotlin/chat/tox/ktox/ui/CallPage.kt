package chat.tox.ktox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.tox.ktox.Call
import chat.tox.ktox.ClientState
import chat.tox.ktox.SHORT_KEY
import chat.tox.ktox.ToxClient
import chat.tox.ktox.VideoFrame
import chat.tox.ktox.duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** The call, on a dark page of its own: the friend's video or picture, ours in a corner, and the buttons. */
@Composable
fun CallPage(
    state: ClientState,
    client: ToxClient,
    navigator: Navigator,
) {
    val call = state.call
    Box(Modifier.fillMaxSize().background(CALL_BACKGROUND)) {
        if (call == null) {
            Text("The call ended.", Modifier.align(Alignment.Center), color = Color.White)
            return@Box
        }
        val friend = state.friends.firstOrNull { it.key == call.friend }
        val name = friend?.displayName ?: call.friend.hex.take(SHORT_KEY)
        val friendVideo = video(client.callVideo.friend, 1).takeIf { call.answered && call.friendVideo }
        if (friendVideo != null) {
            Image(friendVideo, "$name's video", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            // The name and buttons stay readable on a light picture.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(SCRIM_HEIGHT)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))),
            )
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { navigator.back() },
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to the chats", tint = Color.White) }
                if (friendVideo != null) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(callStatus(call), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (friendVideo == null) {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(call.friend.hex, name, size = 128.dp, picture = friend?.avatar)
                        Spacer(Modifier.height(24.dp))
                        Text(
                            name,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(callStatus(call), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium)
                    }
                }
                val self = video(client.callVideo.self, 2).takeIf { call.video }
                if (self != null) {
                    Image(
                        self,
                        "Your video",
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(SELF_WIDTH, SELF_WIDTH * 4 / 3)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            call.problem?.let {
                Text(
                    it,
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    color = Color(0xFFFFC53D),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Controls(call, client)
        }
    }
}

@Composable
private fun Controls(
    call: Call,
    client: ToxClient,
) {
    val platform = LocalPlatform.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))))
            .padding(top = 16.dp, bottom = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!call.outgoing && call.ringing) {
                CallButton(CallIcons.CallEnd, "Decline", RED) { client.hangUp() }
                CallButton(Icons.Default.Call, "Answer", GREEN) { platform.askForCall(video = false) { client.answer(video = false) } }
                if (platform.cameras > 0) {
                    CallButton(
                        CallIcons.Videocam,
                        "With video",
                        GREEN,
                    ) { platform.askForCall(video = true) { client.answer(video = true) } }
                }
                return@Row
            }
            Toggle(if (call.muted) CallIcons.MicOff else CallIcons.Mic, if (call.muted) "Unmute" else "Mute", call.muted) {
                client.setMuted(!call.muted)
            }
            if (platform.cameras > 0) {
                Toggle(if (call.video) CallIcons.Videocam else CallIcons.VideocamOff, "Video", call.video) {
                    if (call.video) client.setVideo(false) else platform.askForCall(video = true) { client.setVideo(true) }
                }
            }
            if (call.video && platform.cameras > 1) {
                Toggle(CallIcons.SwitchCamera, "Flip", false) { client.switchCamera() }
            }
            if (platform.hasEarpiece) {
                Toggle(CallIcons.VolumeUp, "Speaker", call.speaker) { client.setSpeaker(!call.speaker) }
            }
            CallButton(CallIcons.CallEnd, if (call.ringing) "Cancel" else "Hang up", RED) { client.hangUp() }
        }
    }
}

/** A round button that's lit while [on]. */
@Composable
private fun Toggle(
    icon: ImageVector,
    label: String,
    on: Boolean,
    onClick: () -> Unit,
) = CallButton(icon, label, if (on) Color.White else Color.White.copy(alpha = 0.18f), if (on) CALL_BACKGROUND else Color.White, onClick)

@Composable
private fun CallButton(
    icon: ImageVector,
    label: String,
    color: Color,
    content: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = color,
            modifier = Modifier.size(BUTTON_SIZE),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(28.dp), tint = content) }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** A strip above the pages while the user looks at something else during a call; a click goes back to it. */
@Composable
fun CallBar(
    state: ClientState,
    onClick: () -> Unit,
) {
    val call = state.call ?: return
    val name = state.friends.firstOrNull { it.key == call.friend }?.displayName ?: call.friend.hex.take(SHORT_KEY)
    Row(
        Modifier
            .fillMaxWidth()
            .background(GREEN)
            .clickable(onClick = onClick)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Call, null, Modifier.size(18.dp), tint = Color.White)
        Spacer(Modifier.width(12.dp))
        Text(
            "$name · ${callStatus(call)}",
            Modifier.weight(1f),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Back to the call", color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** What the call is doing, with its length ticking once it's answered. */
@Composable
private fun callStatus(call: Call): String {
    val now by produceState(System.currentTimeMillis(), call.started) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000 - value % 1000)
        }
    }
    return when {
        call.outgoing && call.ringing -> "Calling…"
        call.ringing -> if (call.videoCall) "Incoming video call" else "Incoming call"
        !call.friendAudio -> "${duration((now - call.started).coerceAtLeast(0) / 1000)} · they're muted"
        else -> duration((now - call.started).coerceAtLeast(0) / 1000)
    }
}

/** The newest picture from [frames] as an image, every [step]th pixel, made off the UI thread. */
@Composable
private fun video(
    frames: StateFlow<VideoFrame?>,
    step: Int,
): ImageBitmap? {
    val platform = LocalPlatform.current
    val images = remember(platform) { platform.videoImages() }
    val image by produceState<ImageBitmap?>(null, frames) {
        // A StateFlow drops the frames that came while the last one was made.
        frames.collect { frame -> value = frame?.let { withContext(Dispatchers.Default) { images.image(it, step) } } }
    }
    return image
}

private val CALL_BACKGROUND = Color(0xFF111418)
private val RED = Color(0xFFE5484D)
private val GREEN = Color(0xFF2F9E62)
private val BUTTON_SIZE = 64.dp
private val SELF_WIDTH = 108.dp
private val SCRIM_HEIGHT = 120.dp
