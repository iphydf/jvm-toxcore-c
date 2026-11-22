package im.tox.tox4j.core.data

import im.tox.tox4j.core.ToxCoreConstants

class ToxDhtId(
    bytes: ByteArray,
) {
    init {
        require(bytes.size == ToxCoreConstants.DHT_ID_SIZE) {
            "ToxDhtId must be ${ToxCoreConstants.DHT_ID_SIZE} bytes, got ${bytes.size}"
        }
    }

    internal val bytes: ByteArray = bytes.copyOf()

    fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean = this === other || (other is ToxDhtId && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "ToxDhtId(<${bytes.size} bytes>)"
}
