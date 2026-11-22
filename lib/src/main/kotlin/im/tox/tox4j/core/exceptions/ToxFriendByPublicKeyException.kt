package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxFriendByPublicKeyException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** One of the arguments to the function was null when it was not expected. */
        NULL,

        /** No friend with the given Public Key exists on the friend list. */
        NOT_FOUND,
    }
}
