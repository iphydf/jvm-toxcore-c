package im.tox.tox4j.core.data

import im.tox.tox4j.core.ToxCoreConstants

class ToxFileId(
    bytes: ByteArray,
) {
    init {
        require(bytes.size == ToxCoreConstants.FILE_ID_LENGTH) {
            "ToxFileId must be ${ToxCoreConstants.FILE_ID_LENGTH} bytes, got ${bytes.size}"
        }
    }

    internal val bytes: ByteArray = bytes.copyOf()

    fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean = this === other || (other is ToxFileId && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "ToxFileId(<${bytes.size} bytes>)"
}
