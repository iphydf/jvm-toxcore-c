package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxConferenceTitleException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** The conference number passed did not designate a valid conference. */
        CONFERENCE_NOT_FOUND,

        /** The title is too long or empty. */
        INVALID_LENGTH,

        /** The title packet failed to send. */
        FAIL_SEND,
    }
}
