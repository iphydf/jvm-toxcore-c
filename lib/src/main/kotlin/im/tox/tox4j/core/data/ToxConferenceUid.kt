package im.tox.tox4j.core.data

import im.tox.tox4j.core.ToxCoreConstants

class ToxConferenceUid(
    val value: ByteArray,
) {
    init {
        require(value.size == ToxCoreConstants.CONFERENCE_UID_SIZE) {
            "ToxConferenceUid must be ${ToxCoreConstants.CONFERENCE_UID_SIZE} bytes, got ${value.size}"
        }
    }

    override fun equals(other: Any?): Boolean = this === other || (other is ToxConferenceUid && value.contentEquals(other.value))

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = "ToxConferenceUid(<${value.size} bytes>)"
}
