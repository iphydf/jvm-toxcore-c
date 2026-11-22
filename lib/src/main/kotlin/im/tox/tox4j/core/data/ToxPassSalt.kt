package im.tox.tox4j.core.data

import im.tox.tox4j.crypto.ToxCryptoConstants

class ToxPassSalt(
    bytes: ByteArray,
) {
    init {
        require(bytes.size == ToxCryptoConstants.SALT_LENGTH) {
            "ToxPassSalt must be ${ToxCryptoConstants.SALT_LENGTH} bytes, got ${bytes.size}"
        }
    }

    internal val bytes: ByteArray = bytes.copyOf()

    fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean = this === other || (other is ToxPassSalt && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "ToxPassSalt(<${bytes.size} bytes>)"
}
