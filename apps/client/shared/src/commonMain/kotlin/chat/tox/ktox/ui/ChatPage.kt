@file:OptIn(ExperimentalMaterial3Api::class)

package chat.tox.ktox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.tox.ktox.ClientState
import chat.tox.ktox.FileState
import chat.tox.ktox.FileTransfer
import chat.tox.ktox.FriendKey
import chat.tox.ktox.Message
import chat.tox.ktox.SHORT_KEY
import chat.tox.ktox.ToxClient
import chat.tox.ktox.callText
import chat.tox.ktox.isImage
import im.tox.tox4j.core.enums.ToxUserStatus

@Composable
fun ChatPage(
    key: FriendKey,
    state: ClientState,
    client: ToxClient,
    navigator: Navigator,
    wide: Boolean,
) {
    val friend = state.friends.firstOrNull { it.key == key }
    val chat = state.chat?.takeIf { it.friend == key }
    val name = friend?.displayName ?: key.hex.take(SHORT_KEY)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (!wide) IconButton(onClick = { navigator.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                title = {
                    Row(
                        Modifier.clip(MaterialTheme.shapes.medium).clickable { navigator.push(Page.Friend(key)) }.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(key.hex, name, size = 38.dp, friend?.connection, friend?.status ?: ToxUserStatus.NONE, friend?.avatar)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (friend != null) {
                                val colors = MaterialTheme.colorScheme
                                Text(
                                    friend.subtitle(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (friend.typing) colors.primary else colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                actions = {
                    val platform = LocalPlatform.current
                    // Calling back someone who is calling answers them; another profile's call keeps the line.
                    val calling by client.line.profile.collectAsState()
                    val canCall =
                        friend?.online == true &&
                            (calling == null || calling == client.id) &&
                            state.call.let { it == null || (it.friend == key && !it.outgoing) }
                    IconButton(onClick = { platform.askForCall(video = false) { client.call(key, video = false) } }, enabled = canCall) {
                        Icon(Icons.Default.Call, "Call $name")
                    }
                    if (platform.cameras > 0) {
                        IconButton(onClick = { platform.askForCall(video = true) { client.call(key, video = true) } }, enabled = canCall) {
                            Icon(CallIcons.Videocam, "Video call $name")
                        }
                    }
                    IconButton(onClick = { navigator.push(Page.Friend(key)) }) { Icon(Icons.Default.Info, "About $name") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            MessageList(
                chat?.let { Messages(it.messages, { m -> "${m.id.value}" }, Message::time, { m -> "${m.outgoing}" }) },
                hasOlder = chat?.hasOlder == true,
                loadOlder = client::loadOlder,
                empty = "Say hello to $name.",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { message, first, last -> MessageBubble(message, first, last, name, state.progress[message.id], client) }
            val platform = LocalPlatform.current
            MessageField(
                draft = key.hex,
                onSend = { text, action -> client.send(key, text, action) },
                onTyping = { client.setTyping(key, it) },
                onAttach = { platform.pickFile(images = false) { client.sendFile(key, it) } },
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    first: Boolean,
    last: Boolean,
    name: String,
    progress: Long?,
    client: ToxClient,
) {
    val file = message.file
    val picture = file?.state == FileState.DONE && file.isImage
    BubbleFrame(message.outgoing, first, last, tight = picture) { foreground, saysWhen ->
        when {
            message.call != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (message.call.video) CallIcons.Videocam else Icons.Default.Call, null, Modifier.size(18.dp), tint = foreground)
                    Spacer(Modifier.width(8.dp))
                    Text(message.callText(), color = foreground, style = MaterialTheme.typography.bodyLarge)
                }
                StampUnder(stamp(message.time, foreground, saysWhen))
            }

            file == null -> {
                val delivery =
                    when {
                        !message.outgoing -> null
                        message.delivered -> Delivery.DELIVERED
                        else -> Delivery.PENDING
                    }
                StampedText(
                    if (message.action) "* ${if (message.outgoing) "You" else name} ${message.text}" else message.text,
                    foreground,
                    stamp(message.time, foreground, saysWhen, delivery),
                    fontStyle = if (message.action) FontStyle.Italic else FontStyle.Normal,
                )
            }

            picture -> {
                Picture(file)
                StampUnder(stamp(message.time, foreground, saysWhen), Modifier.padding(end = 4.dp))
            }

            else -> {
                FileCard(message, file, progress ?: file.done, foreground, client)
                StampUnder(stamp(message.time, foreground, saysWhen))
            }
        }
    }
}

/** A picture that arrived or went, shown in the chat; a click opens it. */
@Composable
private fun Picture(file: FileTransfer) {
    val platform = LocalPlatform.current
    val image = rememberImage(file.file, file.file.path, PICTURE_PIXELS)
    if (image == null) {
        Box(Modifier.size(PICTURE_WIDTH, PICTURE_WIDTH * 3 / 4))
        return
    }
    Image(
        image,
        file.name,
        Modifier
            .padding(bottom = 4.dp)
            .widthIn(max = PICTURE_WIDTH)
            .heightIn(max = PICTURE_WIDTH)
            .clip(RoundedCornerShape(10.dp))
            .clickable { platform.open(file.file, file.name) },
        contentScale = ContentScale.Fit,
    )
}

/** A file: its name and size, how far it got, and what can be done with it now. */
@Composable
private fun FileCard(
    message: Message,
    file: FileTransfer,
    done: Long,
    foreground: Color,
    client: ToxClient,
) {
    val platform = LocalPlatform.current
    var saved by remember(message.id) { mutableStateOf<String?>(null) }
    val buttons = ButtonDefaults.textButtonColors(contentColor = foreground)
    Column(Modifier.widthIn(min = 220.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(foreground.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    file.name
                        .substringAfterLast('.', "")
                        .take(4)
                        .uppercase()
                        .ifEmpty { "FILE" },
                    color = foreground,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    file.name,
                    color = foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    when (file.state) {
                        FileState.OFFERED -> if (message.outgoing) "${fileSize(file.size)}, waiting" else fileSize(file.size)
                        FileState.ACTIVE -> "${fileSize(done)} of ${fileSize(file.size)}"
                        FileState.DONE -> saved?.let { "Saved in $it" } ?: fileSize(file.size)
                        FileState.CANCELLED -> "${fileSize(file.size)}, cancelled"
                    },
                    color = foreground.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (file.state == FileState.ACTIVE) {
            LinearProgressIndicator(
                progress = { if (file.size > 0) done.toFloat() / file.size else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = foreground,
                trackColor = foreground.copy(alpha = 0.2f),
            )
        }
        Row(Modifier.align(Alignment.End)) {
            when (file.state) {
                FileState.OFFERED if !message.outgoing -> {
                    TextButton(onClick = { client.cancelFile(message) }, colors = buttons) { Text("Decline") }
                    TextButton(onClick = { client.acceptFile(message) }, colors = buttons) { Text("Accept") }
                }

                FileState.OFFERED, FileState.ACTIVE -> {
                    TextButton(onClick = { client.cancelFile(message) }, colors = buttons) { Text("Cancel") }
                }

                FileState.DONE -> {
                    if (!message.outgoing) {
                        TextButton(onClick = { saved = platform.saveToDownloads(file.file, file.name) }, colors = buttons) { Text("Save") }
                    }
                    TextButton(onClick = { platform.open(file.file, file.name) }, colors = buttons) { Text("Open") }
                }

                FileState.CANCELLED -> {}
            }
        }
    }
}

/** How wide pictures in chats are at most, and the pixels they're decoded at for that. */
private val PICTURE_WIDTH = 280.dp
private const val PICTURE_PIXELS = 720
