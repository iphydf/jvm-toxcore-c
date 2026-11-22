package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxGroupSendCustomPacketException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** The group number passed did not designate a valid group. */
        GROUP_NOT_FOUND,

        /** Message length exceeded [ToxCoreConstants.GROUP_MAX_CUSTOM_LOSSY_PACKET_LENGTH] if the packet was lossy, or [ToxCoreConstants.GROUP_MAX_CUSTOM_LOSSLESS_PACKET_LENGTH] if the packet was lossless. */
        TOO_LONG,

        /** The message pointer is null or length is zero. */
        EMPTY,

        /** The group is disconnected. */
        DISCONNECTED,

        /** The packet did not successfully send to any peer. This often indicates a connection issue on the sender's side. */
        FAIL_SEND,
    }
}
