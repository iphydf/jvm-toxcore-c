package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxGroupSelfStatusSetException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** The group number passed did not designate a valid group. */
        GROUP_NOT_FOUND,

        /** The packet failed to send. */
        FAIL_SEND,
    }
}
