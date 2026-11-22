package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxSetTypingException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** The friend number did not designate a valid friend. */
        FRIEND_NOT_FOUND,
    }
}
