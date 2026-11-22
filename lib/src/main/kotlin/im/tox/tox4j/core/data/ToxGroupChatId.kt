package im.tox.tox4j.core.data

import im.tox.tox4j.core.ToxCoreConstants

class ToxGroupChatId(
    bytes: ByteArray,
) {
    init {
        require(bytes.size == ToxCoreConstants.GROUP_CHAT_ID_SIZE) {
            "ToxGroupChatId must be ${ToxCoreConstants.GROUP_CHAT_ID_SIZE} bytes, got ${bytes.size}"
        }
    }

    internal val bytes: ByteArray = bytes.copyOf()

    fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean = this === other || (other is ToxGroupChatId && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "ToxGroupChatId(<${bytes.size} bytes>)"
}
