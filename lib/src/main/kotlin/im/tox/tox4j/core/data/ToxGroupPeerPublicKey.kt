package im.tox.tox4j.core.data

import im.tox.tox4j.core.ToxCoreConstants

class ToxGroupPeerPublicKey(
    bytes: ByteArray,
) {
    init {
        require(bytes.size == ToxCoreConstants.GROUP_PEER_PUBLIC_KEY_SIZE) {
            "ToxGroupPeerPublicKey must be ${ToxCoreConstants.GROUP_PEER_PUBLIC_KEY_SIZE} bytes, got ${bytes.size}"
        }
    }

    internal val bytes: ByteArray = bytes.copyOf()

    fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean = this === other || (other is ToxGroupPeerPublicKey && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "ToxGroupPeerPublicKey(<${bytes.size} bytes>)"
}
