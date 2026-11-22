package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxFriendGetLastOnlineException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** No friend with the given number exists on the friend list. */
        FRIEND_NOT_FOUND,
    }
}
