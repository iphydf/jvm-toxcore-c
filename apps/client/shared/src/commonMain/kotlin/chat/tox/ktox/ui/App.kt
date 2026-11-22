package chat.tox.ktox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chat.tox.ktox.ClientState
import chat.tox.ktox.Platform
import chat.tox.ktox.Profiles
import chat.tox.ktox.SHORT_KEY
import chat.tox.ktox.ToxClient
import kotlinx.coroutines.launch

/** At least this wide, the chat list stays on the left of the page. */
private val TWO_PANES = 720.dp
private val LIST_WIDTH = 360.dp

@Composable
fun App(
    profiles: Profiles,
    platform: Platform,
    navigator: Navigator,
    platformColors: ((dark: Boolean) -> ColorScheme)? = null,
) {
    val clients by profiles.clients.collectAsState()
    val client by profiles.shown.collectAsState()
    val state by client.state.collectAsState()
    val focused = LocalWindowInfo.current.isWindowFocused
    // Only the profile on screen reads its chats.
    LaunchedEffect(focused, client, clients) { clients.forEach { it.setVisible(focused && it == client) } }
    val page = navigator.current
    LaunchedEffect(page, client) {
        client.open((page as? Page.Chat)?.friend)
        client.openGroup((page as? Page.Group)?.group)
    }
    // A call that starts shows itself in its profile, and its page goes when it ends.
    val calling by profiles.line.profile.collectAsState()
    val caller = clients.firstOrNull { it.id == calling }
    val callState = caller?.let { it.state.collectAsState().value }
    val call = callState?.call
    LaunchedEffect(call?.friend, call?.outgoing) {
        if (call == null || caller == null) {
            navigator.drop(Page.Call)
        } else {
            profiles.show(caller.id)
            navigator.push(Page.Call)
        }
    }

    KToxTheme(platformColors) {
        CompositionLocalProvider(LocalPlatform provides platform) {
            Menu(profiles, clients, client, navigator, page) { openMenu ->
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column {
                        val bar = callState?.takeIf { call != null && page != Page.Call }
                        if (bar != null && caller != null) {
                            CallBar(bar) {
                                profiles.show(caller.id)
                                navigator.push(Page.Call)
                            }
                        }
                        // The bar takes the status bar's room.
                        val taken = if (bar != null) WindowInsets.safeDrawing.only(WindowInsetsSides.Top) else WindowInsets(0)
                        Box(Modifier.consumeWindowInsets(taken)) {
                            Panes(state, client, profiles, platform, navigator, page, openMenu)
                        }
                    }
                }
            }
        }
    }
}

/** The profiles, each with its unread count, to switch between, and the pages for all of them. */
@Composable
private fun Menu(
    profiles: Profiles,
    clients: List<ToxClient>,
    shown: ToxClient,
    navigator: Navigator,
    page: Page,
    content: @Composable (openMenu: () -> Unit) -> Unit,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun go(action: () -> Unit) {
        action()
        scope.launch { drawer.close() }
    }

    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet {
                Text("Profiles", Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
                for (client in clients) {
                    ProfileItem(client, selected = client == shown) {
                        go {
                            profiles.show(client.id)
                            navigator.show(Page.Chats)
                        }
                    }
                }
                MenuItem("Add a profile", { Icon(Icons.Default.Add, null) }) {
                    go {
                        profiles.add()
                        navigator.show(Page.Chats)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                MenuItem("Settings", { Icon(Icons.Default.Settings, null) }) { go { navigator.show(Page.Settings) } }
            }
        },
        drawerState = drawer,
        // A swipe from the edge opens it from the chat list; other pages swipe back.
        gesturesEnabled = drawer.isOpen || page == Page.Chats,
    ) { content { scope.launch { drawer.open() } } }
}

@Composable
private fun ProfileItem(
    client: ToxClient,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val state by client.state.collectAsState()
    val profile = state.profile
    val unread = state.friends.sumOf { it.unread } + state.groups.sumOf { it.unread }
    NavigationDrawerItem(
        label = {
            Column {
                Text(
                    profile.name.ifEmpty { profile.address.take(SHORT_KEY).ifEmpty { "New profile" } },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    connectionLabel(profile.connection),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        icon = {
            Avatar(profile.address, profile.name.ifEmpty { "Me" }, size = 36.dp, profile.connection, profile.status, profile.avatar)
        },
        badge = { if (unread > 0) Text("$unread") },
    )
}

@Composable
private fun MenuItem(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        icon = icon,
    )
}

@Composable
private fun Panes(
    state: ClientState,
    client: ToxClient,
    profiles: Profiles,
    platform: Platform,
    navigator: Navigator,
    page: Page,
    openMenu: () -> Unit,
) {
    BoxWithConstraints {
        if (maxWidth >= TWO_PANES) {
            Row {
                Box(Modifier.width(LIST_WIDTH).fillMaxHeight()) {
                    ChatList(state, client, platform, navigator, wide = true, openMenu)
                }
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (page == Page.Chats) {
                        Placeholder()
                    } else {
                        PageContent(page, state, client, profiles, platform, navigator, wide = true, openMenu)
                    }
                }
            }
        } else {
            PageContent(page, state, client, profiles, platform, navigator, wide = false, openMenu)
        }
    }
}

@Composable
private fun PageContent(
    page: Page,
    state: ClientState,
    client: ToxClient,
    profiles: Profiles,
    platform: Platform,
    navigator: Navigator,
    wide: Boolean,
    openMenu: () -> Unit,
) {
    when (page) {
        Page.Chats -> ChatList(state, client, platform, navigator, wide, openMenu)
        is Page.Chat -> ChatPage(page.friend, state, client, navigator, wide)
        is Page.Friend -> FriendPage(page.friend, state, client, platform, navigator)
        is Page.Group -> GroupPage(page.group, state, client, navigator, wide)
        is Page.GroupInfo -> GroupInfoPage(page.group, state, client, platform, navigator)
        Page.Add -> AddPage(state, client, platform, navigator)
        Page.Profile -> ProfilePage(state, client, profiles, platform, navigator)
        Page.Settings -> SettingsPage(state, client, profiles, navigator)
        Page.Call -> CallPage(state, client, navigator)
    }
}

@Composable
private fun Placeholder() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("kTox", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text("Pick a chat on the left.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
