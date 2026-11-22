@file:OptIn(ExperimentalMaterial3Api::class)

package chat.tox.ktox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.tox.ktox.ClientState
import chat.tox.ktox.Friend
import chat.tox.ktox.FriendRequest
import chat.tox.ktox.Group
import chat.tox.ktox.GroupInvite
import chat.tox.ktox.Platform
import chat.tox.ktox.SHORT_KEY
import chat.tox.ktox.ToxClient
import chat.tox.ktox.preview

@Composable
fun ChatList(
    state: ClientState,
    client: ToxClient,
    platform: Platform,
    navigator: Navigator,
    wide: Boolean,
    openMenu: () -> Unit,
) {
    val selected =
        when (val page = navigator.current) {
            is Page.Chat -> page.friend
            is Page.Friend -> page.friend
            else -> null
        }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = openMenu) { Icon(Icons.Default.Menu, "Profiles and settings") } },
                title = {
                    Row(
                        Modifier.clip(MaterialTheme.shapes.medium).clickable { navigator.show(Page.Profile) }.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val profile = state.profile
                        Avatar(
                            profile.address,
                            profile.name.ifEmpty { "Me" },
                            size = 36.dp,
                            profile.connection,
                            profile.status,
                            profile.avatar,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            if (profile.name.isEmpty()) {
                                Text(
                                    "Set your name",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                connectionLabel(profile.connection),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { navigator.show(Page.Add) }) { Icon(Icons.Default.Add, "Add a friend") }
                },
            )
        },
    ) { padding ->
        if (state.friends.isEmpty() && state.requests.isEmpty() && state.groups.isEmpty() && state.invites.isEmpty()) {
            Welcome(state, platform, navigator, Modifier.padding(padding))
            return@Scaffold
        }
        // Friends and groups together, the latest chats first.
        val chats =
            (state.friends.map { it.last?.time to it } + state.groups.map { it.last?.time to it })
                .sortedByDescending { it.first ?: 0L }
                .map { it.second }
        val selectedGroup =
            when (val page = navigator.current) {
                is Page.Group -> page.group
                is Page.GroupInfo -> page.group
                else -> null
            }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(state.requests, key = { "request ${it.key.hex}" }) { RequestCard(it, client) }
            items(state.invites, key = { "invite ${it.data}" }) { InviteCard(it, state, client) }
            items(chats, key = { if (it is Friend) it.key.hex else (it as Group).id.hex }) { chat ->
                when (chat) {
                    is Friend -> FriendRow(chat, selected = wide && chat.key == selected) { navigator.show(Page.Chat(chat.key)) }
                    is Group -> GroupRow(chat, selected = wide && chat.id == selectedGroup) { navigator.show(Page.Group(chat.id)) }
                }
            }
        }
    }
}

@Composable
private fun FriendRow(
    friend: Friend,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) colors.secondaryContainer else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(friend.key.hex, friend.displayName, connection = friend.connection, status = friend.status, picture = friend.avatar)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    friend.displayName,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (friend.unread > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                friend.last?.let {
                    Text(shortTime(it.time), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val last = friend.last
                Text(
                    when {
                        friend.typing -> "typing…"
                        last == null -> friend.subtitle()
                        last.action -> "* ${if (last.outgoing) "You" else friend.displayName} ${last.text}"
                        last.outgoing -> "You: ${last.preview}"
                        else -> last.preview
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (friend.typing) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (friend.unread > 0) {
                    Badge(containerColor = colors.primary) { Text("${friend.unread}") }
                }
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: Group,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) colors.secondaryContainer else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupAvatar(group)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    group.displayName,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (group.unread > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                group.last?.let {
                    Text(shortTime(it.time), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val last = group.last
                Text(
                    when {
                        last == null -> group.subtitle()
                        last.action -> "* ${if (last.outgoing) "You" else last.peerName} ${last.text}"
                        else -> "${if (last.outgoing) "You" else last.peerName}: ${last.text}"
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (group.unread > 0) {
                    Badge(containerColor = colors.primary) { Text("${group.unread}") }
                }
            }
        }
    }
}

@Composable
private fun InviteCard(
    invite: GroupInvite,
    state: ClientState,
    client: ToxClient,
) {
    val from = state.friends.firstOrNull { it.key == invite.friend }?.displayName ?: invite.friend.hex.take(SHORT_KEY)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Group invite from $from", style = MaterialTheme.typography.titleSmall)
            Text(invite.name.ifEmpty { "A group" }, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { client.declineInvite(invite) }) { Text("Decline") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { client.acceptInvite(invite) }) { Text("Join") }
            }
        }
    }
}

@Composable
private fun RequestCard(
    request: FriendRequest,
    client: ToxClient,
) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(request.key.hex, request.key.hex, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Friend request", style = MaterialTheme.typography.titleSmall)
                    Text(
                        groupedId(request.key.hex),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            if (request.message.isNotBlank()) {
                Text(request.message, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { client.decline(request) }) { Text("Decline") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { client.accept(request) }) { Text("Accept") }
            }
        }
    }
}

@Composable
private fun Welcome(
    state: ClientState,
    platform: Platform,
    navigator: Navigator,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No chats yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add a friend with their Tox ID, or give them yours.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { navigator.show(Page.Add) }) { Text("Add a friend") }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = { platform.copy(state.profile.address) }, enabled = state.profile.address.isNotEmpty()) {
            Text("Copy my Tox ID (${state.profile.address.take(SHORT_KEY)}…)")
        }
    }
}
