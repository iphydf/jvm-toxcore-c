package im.tox.tox4j.crypto.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxGetSaltException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** One of the arguments to the function was null when it was not expected. */
        NULL,

        /** The input data is missing the magic number (i.e. wasn't created by this module, or is corrupted). */
        BAD_FORMAT,
    }
}
