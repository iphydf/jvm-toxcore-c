package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxGroupKickPeerException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** The group number passed did not designate a valid group. */
        GROUP_NOT_FOUND,

        /** The ID passed did not designate a valid peer. */
        PEER_NOT_FOUND,

        /** The caller does not have the required permissions for this action. */
        PERMISSIONS,

        /** The peer could not be kicked from the group. */
        FAIL_ACTION,

        /** The packet failed to send. */
        FAIL_SEND,

        /** The caller attempted to set their own role. */
        SELF,
    }
}
