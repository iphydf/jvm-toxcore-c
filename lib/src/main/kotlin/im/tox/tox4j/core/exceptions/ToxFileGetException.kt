package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxFileGetException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** One of the arguments to the function was null when it was not expected. */
        NULL,

        /** The friend_number passed did not designate a valid friend. */
        FRIEND_NOT_FOUND,

        /** No file transfer with the given file number was found for the given friend. */
        NOT_FOUND,
    }
}
