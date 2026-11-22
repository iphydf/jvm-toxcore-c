package im.tox.tox4j.core.data

import im.tox.tox4j.core.ToxCoreConstants

class ToxSecretKey(
    bytes: ByteArray,
) {
    init {
        require(bytes.size == ToxCoreConstants.SECRET_KEY_SIZE) {
            "ToxSecretKey must be ${ToxCoreConstants.SECRET_KEY_SIZE} bytes, got ${bytes.size}"
        }
    }

    internal val bytes: ByteArray = bytes.copyOf()

    fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean = this === other || (other is ToxSecretKey && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "ToxSecretKey(<${bytes.size} bytes>)"
}
