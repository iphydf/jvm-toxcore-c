@file:OptIn(ExperimentalMaterial3Api::class)

package chat.tox.ktox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import chat.tox.ktox.ClientState
import chat.tox.ktox.FriendKey
import chat.tox.ktox.Platform
import chat.tox.ktox.Profiles
import chat.tox.ktox.SHORT_KEY
import chat.tox.ktox.ToxClient
import im.tox.tox4j.core.ToxCoreConstants
import im.tox.tox4j.core.enums.ToxConnection
import im.tox.tox4j.core.enums.ToxUserStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A page with a title and a back arrow, its content in a readable column. */
@Composable
internal fun PageScaffold(
    title: String,
    navigator: Navigator,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navigator.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

@Composable
internal fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/** A key or Tox ID people can select, copy and read out. */
@Composable
internal fun Key(
    value: String,
    platform: Platform,
) {
    SelectionContainer {
        Text(groupedId(value), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    }
    FilledTonalButton(onClick = { platform.copy(value) }) { Text("Copy") }
}

@Composable
fun ProfilePage(
    state: ClientState,
    client: ToxClient,
    profiles: Profiles,
    platform: Platform,
    navigator: Navigator,
) {
    val profile = state.profile
    val scope = rememberCoroutineScope()
    var problem by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val clients by profiles.clients.collectAsState()
    val calling by profiles.line.profile.collectAsState()

    fun pickPicture() =
        platform.pickFile(images = true) { picked ->
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { picked.open().use { it.readBytes() } }
                problem = if (client.setAvatar(bytes)) null else "That isn't a picture kTox can read."
            }
        }

    PageScaffold("Me", navigator) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.clickable(onClick = ::pickPicture)) {
                Avatar(profile.address, profile.name.ifEmpty { "Me" }, size = 96.dp, profile.connection, profile.status, profile.avatar)
            }
            Row {
                TextButton(onClick = ::pickPicture) { Text(if (profile.avatar == null) "Add a picture" else "Change picture") }
                if (profile.avatar != null) {
                    TextButton(onClick = { scope.launch { client.setAvatar(null) } }) { Text("Remove") }
                }
            }
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(
                when (profile.connection) {
                    ToxConnection.NONE -> "Connecting to the Tox network…"
                    ToxConnection.TCP -> "Online through a TCP relay"
                    ToxConnection.UDP -> "Online"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Section("Name and status") {
            var name by rememberSaveable(profile.name) { mutableStateOf(profile.name) }
            var message by rememberSaveable(profile.statusMessage) { mutableStateOf(profile.statusMessage) }
            OutlinedTextField(
                name,
                { if (it.encodeToByteArray().size <= ToxCoreConstants.MAX_NAME_LENGTH) name = it },
                Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
            )
            OutlinedTextField(
                message,
                { if (it.encodeToByteArray().size <= ToxCoreConstants.MAX_STATUS_MESSAGE_LENGTH) message = it },
                Modifier.fillMaxWidth(),
                label = { Text("Status message") },
            )
            Button(
                onClick = {
                    if (name != profile.name) client.setName(name)
                    if (message != profile.statusMessage) client.setStatusMessage(message)
                },
                enabled = name != profile.name || message != profile.statusMessage,
            ) { Text("Save") }
            val statuses = listOf(ToxUserStatus.NONE to "Online", ToxUserStatus.AWAY to "Away", ToxUserStatus.BUSY to "Busy")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                statuses.forEachIndexed { i, (status, label) ->
                    SegmentedButton(
                        selected = profile.status == status,
                        onClick = { client.setStatus(status) },
                        shape = SegmentedButtonDefaults.itemShape(i, statuses.size),
                    ) { Text(label) }
                }
            }
        }
        Section("My Tox ID") {
            Text(
                "Friends add you with this. It's safe to share.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (profile.address.isNotEmpty()) {
                QrCode("tox:${profile.address}", Modifier.align(Alignment.CenterHorizontally).size(220.dp))
            }
            Key(profile.address, platform)
        }
        if (clients.size > 1 && calling != client.id) {
            Section("Delete") {
                Text(
                    "Deletes this profile, its Tox ID and its chats from this device. Your other profiles stay.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { confirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete this profile") }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete ${profile.name.ifEmpty { "this profile" }}?") },
            text = { Text("Its friends can't reach this Tox ID again. Its chats go too.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    profiles.delete(client.id)
                    navigator.show(Page.Chats)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun AddPage(
    state: ClientState,
    client: ToxClient,
    platform: Platform,
    navigator: Navigator,
) {
    var id by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    PageScaffold("Add", navigator) {
        Section("Their Tox ID") {
            OutlinedTextField(
                id,
                {
                    id = it.filterNot(Char::isWhitespace)
                    problem = null
                },
                Modifier.fillMaxWidth(),
                label = { Text("Tox ID") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                isError = problem != null,
                supportingText = { problem?.let { Text(it) } },
            )
            OutlinedTextField(
                message,
                { if (it.encodeToByteArray().size <= ToxCoreConstants.MAX_FRIEND_REQUEST_LENGTH) message = it },
                Modifier.fillMaxWidth(),
                label = { Text("Message (optional)") },
            )
            Button(
                onClick = {
                    scope.launch {
                        problem = client.addFriend(id, message)
                        if (problem == null) navigator.show(Page.Chats)
                    }
                },
                enabled = id.isNotEmpty(),
            ) { Text("Send friend request") }
        }
        Section("Or give them yours") {
            Key(state.profile.address, platform)
        }
        NewGroup(client, navigator)
        JoinGroup(client, navigator)
    }
}

@Composable
private fun NewGroup(
    client: ToxClient,
    navigator: Navigator,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var private by rememberSaveable { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Section("Start a group") {
        OutlinedTextField(
            name,
            { if (it.encodeToByteArray().size <= ToxCoreConstants.GROUP_MAX_GROUP_NAME_LENGTH) name = it },
            Modifier.fillMaxWidth(),
            label = { Text("Group name") },
            singleLine = true,
            isError = problem != null,
            supportingText = { problem?.let { Text(it) } },
        )
        Toggle("Private", "Only people you invite can join. Public groups can be joined with their chat ID.", private) { private = it }
        Button(
            onClick = {
                scope.launch {
                    problem = client.createGroup(name.trim(), private)
                    if (problem == null) navigator.show(Page.Chats)
                }
            },
            enabled = name.isNotBlank(),
        ) { Text("Start the group") }
    }
}

@Composable
private fun JoinGroup(
    client: ToxClient,
    navigator: Navigator,
) {
    var id by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Section("Join a public group") {
        OutlinedTextField(
            id,
            {
                id = it.filterNot(Char::isWhitespace)
                problem = null
            },
            Modifier.fillMaxWidth(),
            label = { Text("Chat ID") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            isError = problem != null,
            supportingText = { problem?.let { Text(it) } },
        )
        OutlinedTextField(
            password,
            { if (it.encodeToByteArray().size <= ToxCoreConstants.GROUP_MAX_PASSWORD_SIZE) password = it },
            Modifier.fillMaxWidth(),
            label = { Text("Password, if it has one") },
            singleLine = true,
        )
        Button(
            onClick = {
                scope.launch {
                    problem = client.joinGroup(id, password)
                    if (problem == null) navigator.show(Page.Chats)
                }
            },
            enabled = id.isNotEmpty(),
        ) { Text("Join") }
    }
}

@Composable
fun SettingsPage(
    state: ClientState,
    client: ToxClient,
    profiles: Profiles,
    navigator: Navigator,
) {
    val settings = state.settings
    val stayConnected by profiles.stayConnected.collectAsState()
    PageScaffold("Settings", navigator) {
        Section("Network") {
            Text(
                "For ${state.profile.name.ifEmpty { "this profile" }}. Changes reconnect it to the Tox network.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Toggle("UDP", "Faster. Off: all traffic goes through TCP relays.", settings.udp) {
                client.updateSettings(settings.copy(udp = it))
            }
            HorizontalDivider()
            Toggle("IPv6", "Also connect over IPv6.", settings.ipv6) {
                client.updateSettings(settings.copy(ipv6 = it))
            }
            HorizontalDivider()
            Toggle("Local discovery", "Find Tox users on the same network directly.", settings.localDiscovery) {
                client.updateSettings(settings.copy(localDiscovery = it))
            }
        }
        if (LocalPlatform.current.canStayConnected) {
            Section("Phone") {
                Toggle(
                    "Stay connected",
                    "Messages keep arriving for every profile while kTox is closed. It shows a notification while it does.",
                    stayConnected,
                    profiles::setStayConnected,
                )
            }
        }
    }
}

@Composable
internal fun Toggle(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.clip(MaterialTheme.shapes.small).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
fun FriendPage(
    key: FriendKey,
    state: ClientState,
    client: ToxClient,
    platform: Platform,
    navigator: Navigator,
) {
    val friend = state.friends.firstOrNull { it.key == key }
    val name = friend?.displayName ?: key.hex.take(SHORT_KEY)
    var confirm by remember { mutableStateOf(false) }
    PageScaffold(name, navigator) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(key.hex, name, size = 96.dp, friend?.connection, friend?.status ?: ToxUserStatus.NONE, friend?.avatar)
            Spacer(Modifier.height(8.dp))
            Text(name, style = MaterialTheme.typography.headlineSmall)
            if (friend != null) {
                Text(presence(friend.connection, friend.status), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (friend.statusMessage.isNotBlank()) {
                    Text(friend.statusMessage, Modifier.padding(top = 8.dp))
                }
            }
        }
        Section("Public key") {
            Key(key.hex, platform)
        }
        Section("Remove") {
            Text(
                "Removes $name and your chat with them from this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { confirm = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Remove friend") }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Remove $name?") },
            text = { Text("Your chat with them goes too. They can send you a friend request again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    client.delete(key)
                    navigator.show(Page.Chats)
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
