package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxGroupSelfNameSetException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** The group number passed did not designate a valid group. */
        GROUP_NOT_FOUND,

        /** Name length exceeded [ToxCoreConstants.MAX_NAME_LENGTH]. */
        TOO_LONG,

        /** The length given to the set function is zero or name is a null pointer. */
        INVALID,

        /** The packet failed to send. */
        FAIL_SEND,
    }
}
