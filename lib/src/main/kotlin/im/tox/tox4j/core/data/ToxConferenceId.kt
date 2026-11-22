package im.tox.tox4j.core.data

import im.tox.tox4j.core.ToxCoreConstants

class ToxConferenceId(
    bytes: ByteArray,
) {
    init {
        require(bytes.size == ToxCoreConstants.CONFERENCE_ID_SIZE) {
            "ToxConferenceId must be ${ToxCoreConstants.CONFERENCE_ID_SIZE} bytes, got ${bytes.size}"
        }
    }

    internal val bytes: ByteArray = bytes.copyOf()

    fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean = this === other || (other is ToxConferenceId && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "ToxConferenceId(<${bytes.size} bytes>)"
}
