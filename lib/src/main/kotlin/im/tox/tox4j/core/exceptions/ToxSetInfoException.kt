package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxSetInfoException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** One of the arguments to the function was null when it was not expected. */
        NULL,

        /** Information length exceeded maximum permissible size. */
        TOO_LONG,
    }
}
