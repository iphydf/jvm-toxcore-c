package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

/**
 * An exception thrown when a group voice state could not be set.
 */
class ToxGroupSetVoiceStateException : ToxException {
    enum class Code {
        /** The group number passed did not designate a valid group. */
        GROUP_NOT_FOUND,

        /** The caller does not have the required permissions to set the privacy state. */
        PERMISSIONS,

        /**
         * The voice state could not be set. This may occur due to an error related to cryptographic
         * signing of the new shared state.
         */
        FAIL_SET,

        /** The packet failed to send. */
        FAIL_SEND,

        /** The group is disconnected. */
        DISCONNECTED,
    }

    constructor(code: Code) : this(code, "")

    constructor(code: Code, message: String) : super(code, message)
}
