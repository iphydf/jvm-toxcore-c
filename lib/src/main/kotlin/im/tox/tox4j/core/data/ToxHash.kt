package im.tox.tox4j.core.data

import im.tox.tox4j.crypto.ToxCryptoConstants

class ToxHash(
    bytes: ByteArray,
) {
    init {
        require(bytes.size == ToxCryptoConstants.HASH_LENGTH) {
            "ToxHash must be ${ToxCryptoConstants.HASH_LENGTH} bytes, got ${bytes.size}"
        }
    }

    internal val bytes: ByteArray = bytes.copyOf()

    fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean = this === other || (other is ToxHash && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "ToxHash(<${bytes.size} bytes>)"
}
