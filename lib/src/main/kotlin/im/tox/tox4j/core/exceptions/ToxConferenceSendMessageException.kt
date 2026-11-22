package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxConferenceSendMessageException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** The conference number passed did not designate a valid conference. */
        CONFERENCE_NOT_FOUND,

        /** The message is too long. */
        TOO_LONG,

        /** The client is not connected to the conference. */
        NO_CONNECTION,

        /** The message packet failed to send. */
        FAIL_SEND,
    }
}
