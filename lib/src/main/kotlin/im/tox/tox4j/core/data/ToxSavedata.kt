package im.tox.tox4j.core.data

/**
 * A typed byte array for ToxSavedata.
 *
 * Plain `class` rather than `@JvmInline value class`: a value class
 * over `ByteArray` inherits reference equality (and can't override
 * `equals`), so two instances with identical bytes compare unequal.
 * `toString` omits payload bytes — these can carry user-facing text
 * or sensitive material that shouldn't land in logs.
 */
class ToxSavedata(
    val value: ByteArray,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is ToxSavedata && value.contentEquals(other.value))

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = "ToxSavedata(<${value.size} bytes>)"
}
