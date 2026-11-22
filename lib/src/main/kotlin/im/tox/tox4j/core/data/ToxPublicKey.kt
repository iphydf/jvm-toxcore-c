package im.tox.tox4j.core.data

import im.tox.tox4j.core.ToxCoreConstants

class ToxPublicKey(
    bytes: ByteArray,
) {
    init {
        require(bytes.size == ToxCoreConstants.PUBLIC_KEY_SIZE) {
            "ToxPublicKey must be ${ToxCoreConstants.PUBLIC_KEY_SIZE} bytes, got ${bytes.size}"
        }
    }

    internal val bytes: ByteArray = bytes.copyOf()

    fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean = this === other || (other is ToxPublicKey && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "ToxPublicKey(<${bytes.size} bytes>)"
}
