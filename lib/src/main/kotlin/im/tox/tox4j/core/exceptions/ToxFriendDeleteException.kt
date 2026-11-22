package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxFriendDeleteException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** There was no friend with the given friend number. No friends were deleted. */
        FRIEND_NOT_FOUND,
    }
}
