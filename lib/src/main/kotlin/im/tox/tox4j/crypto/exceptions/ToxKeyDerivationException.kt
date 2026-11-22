package im.tox.tox4j.crypto.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxKeyDerivationException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** One of the arguments to the function was null when it was not expected. */
        NULL,

        /** The crypto lib was unable to derive a key from the given passphrase, which is usually a lack of memory issue. */
        FAILED,
    }
}
