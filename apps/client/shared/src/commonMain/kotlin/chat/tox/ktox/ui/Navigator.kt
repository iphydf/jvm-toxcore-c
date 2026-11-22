package chat.tox.ktox.ui

import androidx.compose.runtime.mutableStateListOf
import chat.tox.ktox.FriendKey
import chat.tox.ktox.GroupId

sealed interface Page {
    data object Chats : Page

    data class Chat(
        val friend: FriendKey,
    ) : Page

    data class Friend(
        val friend: FriendKey,
    ) : Page

    data class Group(
        val group: GroupId,
    ) : Page

    data class GroupInfo(
        val group: GroupId,
    ) : Page

    data object Add : Page

    data object Profile : Page

    data object Settings : Page

    /** The call there is, if there's one. */
    data object Call : Page
}

/** Where the user is: the chat list, then at most a page or two on top. */
class Navigator {
    private val stack = mutableStateListOf<Page>(Page.Chats)

    val current: Page get() = stack.last()

    val canGoBack: Boolean get() = stack.size > 1

    /** Goes to [page] from the chat list, like a notification or a tap in the list does. */
    fun show(page: Page) {
        stack.clear()
        stack += Page.Chats
        if (page != Page.Chats) stack += page
    }

    /** Goes to [page] on top of the current one. */
    fun push(page: Page) {
        if (current != page) stack += page
    }

    /** Takes [page] out wherever it is, as when a call ends. */
    fun drop(page: Page) {
        stack.removeAll { it == page }
        if (stack.isEmpty()) stack += Page.Chats
    }

    fun back(): Boolean {
        if (!canGoBack) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}
