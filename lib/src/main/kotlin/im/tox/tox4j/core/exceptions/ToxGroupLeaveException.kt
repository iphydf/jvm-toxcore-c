package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxGroupLeaveException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** The group number passed did not designate a valid group. */
        GROUP_NOT_FOUND,

        /** Message length exceeded [ToxCoreConstants.GROUP_MAX_PART_LENGTH]. */
        TOO_LONG,

        /** The parting packet failed to send. */
        FAIL_SEND,
    }
}
