package im.tox.tox4j.core.data

import im.tox.tox4j.core.ToxCoreConstants

class ToxAddress(
    bytes: ByteArray,
) {
    init {
        require(bytes.size == ToxCoreConstants.ADDRESS_SIZE) {
            "ToxAddress must be ${ToxCoreConstants.ADDRESS_SIZE} bytes, got ${bytes.size}"
        }
    }

    internal val bytes: ByteArray = bytes.copyOf()

    fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean = this === other || (other is ToxAddress && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "ToxAddress(<${bytes.size} bytes>)"
}
