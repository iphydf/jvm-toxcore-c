package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

/**
 * An exception thrown when the port of a Tox instance could not be retrieved.
 */
class ToxGetPortException : ToxException {
    enum class Code {
        /** The instance was not bound to any port. */
        NOT_BOUND,
    }

    constructor(code: Code) : this(code, "")

    constructor(code: Code, message: String) : super(code, message)
}
