package im.tox.tox4j.core.data

/**
 * A typed byte array for ToxGroupCustomPacket.
 *
 * Not a value class: those can't override `equals` to compare the bytes.
 * `toString` leaves out the bytes, which can be private.
 */
class ToxGroupCustomPacket(
    val value: ByteArray,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is ToxGroupCustomPacket && value.contentEquals(other.value))

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = "ToxGroupCustomPacket(<${value.size} bytes>)"
}
