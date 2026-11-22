@file:OptIn(ExperimentalMaterial3Api::class)

package chat.tox.ktox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.tox.ktox.ClientState
import chat.tox.ktox.Group
import chat.tox.ktox.GroupId
import chat.tox.ktox.GroupMessage
import chat.tox.ktox.Peer
import chat.tox.ktox.Platform
import chat.tox.ktox.SHORT_KEY
import chat.tox.ktox.ToxClient
import im.tox.tox4j.core.ToxCoreConstants
import im.tox.tox4j.core.enums.ToxGroupPrivacyState
import im.tox.tox4j.core.enums.ToxGroupRole

@Composable
fun GroupPage(
    id: GroupId,
    state: ClientState,
    client: ToxClient,
    navigator: Navigator,
    wide: Boolean,
) {
    val group = state.groups.firstOrNull { it.id == id }
    val chat = state.groupChat?.takeIf { it.group == id }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (!wide) IconButton(onClick = { navigator.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                title = {
                    Row(
                        Modifier.clip(MaterialTheme.shapes.medium).clickable { navigator.push(Page.GroupInfo(id)) }.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (group != null) GroupAvatar(group, size = 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                group?.displayName ?: id.hex.take(SHORT_KEY),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (group != null) {
                                Text(
                                    group.subtitle(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { navigator.push(Page.GroupInfo(id)) }) { Icon(Icons.Default.Info, "About the group") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            MessageList(
                chat?.let {
                    Messages(
                        it.messages,
                        { m -> "${m.id.value}" },
                        GroupMessage::time,
                        { m -> if (m.outgoing) "" else m.peerKey },
                    )
                },
                hasOlder = chat?.hasOlder == true,
                loadOlder = client::loadOlderGroupMessages,
                empty = "No messages yet.",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { message, first, last -> GroupBubble(message, first, last) }
            if (group != null && !group.connected) {
                Text(
                    group.problem ?: "Connecting to the group: messages can't go out yet.",
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (group.problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MessageField(draft = "group ${id.hex}", onSend = { text, action -> client.sendGroupMessage(id, text, action) })
        }
    }
}

/** A colour of its own for each peer's name, readable on the incoming bubble. */
@Composable
private fun peerColor(key: String): Color {
    val hue = (key.take(6).toIntOrNull(16) ?: 0) % 360
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return Color.hsl(hue.toFloat(), 0.6f, if (dark) 0.7f else 0.4f)
}

private fun Color.luminance() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
private fun GroupBubble(
    message: GroupMessage,
    first: Boolean,
    last: Boolean,
) {
    BubbleFrame(message.outgoing, first, last) { foreground, saysWhen ->
        if (first && !message.outgoing) {
            Text(
                message.peerName.ifEmpty { message.peerKey.take(SHORT_KEY) },
                color = peerColor(message.peerKey),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (message.private) {
            Text(
                if (message.outgoing) "Only to them" else "Only to you",
                color = foreground.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        val failed = message.outgoing && !message.sent
        StampedText(
            if (message.action) "* ${if (message.outgoing) "You" else message.peerName} ${message.text}" else message.text,
            foreground,
            if (failed) null else stamp(message.time, foreground, saysWhen, Delivery.SENT.takeIf { message.outgoing }),
            fontStyle = if (message.action) FontStyle.Italic else FontStyle.Normal,
        )
        if (failed) {
            Text("Not sent", Modifier.align(Alignment.End), color = foreground, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun GroupInfoPage(
    id: GroupId,
    state: ClientState,
    client: ToxClient,
    platform: Platform,
    navigator: Navigator,
) {
    val group = state.groups.firstOrNull { it.id == id }
    var leaving by remember { mutableStateOf(false) }
    PageScaffold(group?.displayName ?: "Group", navigator) {
        if (group == null) return@PageScaffold
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            GroupAvatar(group, size = 96.dp)
            Spacer(Modifier.height(8.dp))
            Text(group.displayName, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${if (group.privacy == ToxGroupPrivacyState.PRIVATE) "Private" else "Public"} group · ${group.subtitle()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Topic(group, client)
        Invite(group, state, client)
        if (group.privacy == ToxGroupPrivacyState.PUBLIC) {
            Section("Chat ID") {
                Text(
                    "Anyone can join with this.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Key(group.id.hex, platform)
            }
        }
        People(group, client)
        if (group.selfRole == ToxGroupRole.FOUNDER) GroupSettings(group, client)
        Section("Leave") {
            OutlinedButton(
                onClick = { leaving = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Leave the group") }
        }
    }
    if (leaving && group != null) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("Leave ${group.displayName}?") },
            text = { Text("The chat goes from this device too.") },
            confirmButton = {
                TextButton(onClick = {
                    leaving = false
                    client.leaveGroup(id)
                    navigator.show(Page.Chats)
                }) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Topic(
    group: Group,
    client: ToxClient,
) {
    Section("Topic") {
        if (!group.canSetTopic) {
            Text(group.topic.ifBlank { "No topic." })
            return@Section
        }
        var topic by rememberSaveable(group.topic) { mutableStateOf(group.topic) }
        OutlinedTextField(
            topic,
            { if (it.encodeToByteArray().size <= ToxCoreConstants.GROUP_MAX_TOPIC_LENGTH) topic = it },
            Modifier.fillMaxWidth(),
            label = { Text("Topic") },
        )
        Button(onClick = { client.setTopic(group.id, topic) }, enabled = topic != group.topic && group.connected) { Text("Set the topic") }
    }
}

@Composable
private fun Invite(
    group: Group,
    state: ClientState,
    client: ToxClient,
) {
    // Only online friends can get an invite; friends who are in already don't need one.
    val online = state.friends.filter { it.online }
    var invited by remember(group.id) { mutableStateOf(setOf<String>()) }
    Section("Invite friends") {
        if (online.isEmpty()) {
            Text("Friends who are online show up here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (friend in online) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(friend.key.hex, friend.displayName, size = 36.dp, picture = friend.avatar)
                Spacer(Modifier.width(12.dp))
                Text(friend.displayName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sent = friend.key.hex in invited
                FilledTonalButton(
                    onClick = {
                        client.inviteFriend(group.id, friend.key)
                        invited = invited + friend.key.hex
                    },
                    enabled = !sent && group.connected,
                ) { Text(if (sent) "Invited" else "Invite") }
            }
        }
    }
}

@Composable
private fun People(
    group: Group,
    client: ToxClient,
) {
    Section("People") {
        group.peers.forEachIndexed { i, peer ->
            if (i > 0) HorizontalDivider()
            PeerRow(group, peer, client)
        }
    }
}

private fun ToxGroupRole.label() =
    when (this) {
        ToxGroupRole.FOUNDER -> "Founder"
        ToxGroupRole.MODERATOR -> "Moderator"
        ToxGroupRole.USER -> ""
        ToxGroupRole.OBSERVER -> "Can only read"
    }

@Composable
private fun PeerRow(
    group: Group,
    peer: Peer,
    client: ToxClient,
) {
    var menu by remember { mutableStateOf(false) }
    // Founders manage everyone else; moderators manage users and observers.
    val manageable =
        !peer.self &&
            when (group.selfRole) {
                ToxGroupRole.FOUNDER -> peer.role != ToxGroupRole.FOUNDER
                ToxGroupRole.MODERATOR -> peer.role == ToxGroupRole.USER || peer.role == ToxGroupRole.OBSERVER
                else -> false
            }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(peer.key, peer.displayName, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (peer.self) "${peer.displayName} (you)" else peer.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            peer.role.label().takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (manageable) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Manage ${peer.displayName}") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    val roles =
                        if (group.selfRole == ToxGroupRole.FOUNDER) {
                            listOf(
                                ToxGroupRole.MODERATOR to "Make moderator",
                                ToxGroupRole.USER to "Make user",
                                ToxGroupRole.OBSERVER to "Only let them read",
                            )
                        } else {
                            listOf(ToxGroupRole.USER to "Let them write", ToxGroupRole.OBSERVER to "Only let them read")
                        }
                    for ((role, label) in roles.filter { it.first != peer.role }) {
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            menu = false
                            client.setRole(group.id, peer.key, role)
                        })
                    }
                    DropdownMenuItem(text = { Text("Remove from the group", color = MaterialTheme.colorScheme.error) }, onClick = {
                        menu = false
                        client.kick(group.id, peer.key)
                    })
                }
            }
        }
    }
}

@Composable
private fun GroupSettings(
    group: Group,
    client: ToxClient,
) {
    Section("Settings") {
        Toggle("Private", "Only invited friends can join.", group.privacy == ToxGroupPrivacyState.PRIVATE) {
            client.setGroupPrivacy(group.id, it)
        }
        HorizontalDivider()
        Toggle("Lock the topic", "Only moderators can change it.", group.topicLocked) { client.setTopicLock(group.id, it) }
        HorizontalDivider()
        var password by rememberSaveable(group.password) { mutableStateOf(group.password) }
        OutlinedTextField(
            password,
            { if (it.encodeToByteArray().size <= ToxCoreConstants.GROUP_MAX_PASSWORD_SIZE) password = it },
            Modifier.fillMaxWidth(),
            label = { Text("Password: empty for none") },
            singleLine = true,
        )
        var limit by rememberSaveable(group.peerLimit) { mutableStateOf(group.peerLimit.toString()) }
        OutlinedTextField(
            limit,
            { if (it.all(Char::isDigit) && it.length <= PEER_LIMIT_DIGITS) limit = it },
            Modifier.fillMaxWidth(),
            label = { Text("Most people at once") },
            singleLine = true,
        )
        Button(
            onClick = {
                if (password != group.password) client.setGroupPassword(group.id, password)
                limit.toIntOrNull()?.takeIf { it > 0 && it != group.peerLimit }?.let { client.setPeerLimit(group.id, it) }
            },
            enabled = group.connected && (password != group.password || limit != group.peerLimit.toString()),
        ) { Text("Save") }
    }
}

/** The peer limit is 16 bits. */
private const val PEER_LIMIT_DIGITS = 5
