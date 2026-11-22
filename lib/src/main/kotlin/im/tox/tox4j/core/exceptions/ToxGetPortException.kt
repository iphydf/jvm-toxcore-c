package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxGetPortException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** The instance was not bound to any port. */
        NOT_BOUND,
    }
}
