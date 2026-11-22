package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxGroupInviteFriendException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** The group number passed did not designate a valid group. */
        GROUP_NOT_FOUND,

        /** The friend number passed did not designate a valid friend. */
        FRIEND_NOT_FOUND,

        /** Creation of the invite packet failed. This indicates a network related error. */
        INVITE_FAIL,

        /** Packet failed to send. */
        FAIL_SEND,

        /** The group is disconnected. */
        DISCONNECTED,
    }
}
