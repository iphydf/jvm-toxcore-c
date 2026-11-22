package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxConferenceNewException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** The conference instance failed to initialize. */
        INIT,
    }
}
