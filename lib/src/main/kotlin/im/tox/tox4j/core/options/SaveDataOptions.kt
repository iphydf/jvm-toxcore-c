package im.tox.tox4j.core.options

import im.tox.tox4j.core.ToxCore
import im.tox.tox4j.core.data.ToxSecretKey
import im.tox.tox4j.core.enums.ToxSavedataType

/** Base type for all save data kinds. */
@Suppress("ktlint:standard:no-consecutive-comments")
object SaveDataOptions {
    sealed interface Type {
        /** The low level [ToxSavedataType] enum to pass to [ToxCore]. */
        val kind: ToxSavedataType

        /** Serialised save data. The format depends on [kind]. */
        val data: ByteArray
    }

    /** The various kinds of save data that can be loaded by [ToxCore]. */

    /** No save data. */
    object None : Type {
        override val kind: ToxSavedataType = ToxSavedataType.NONE
        override val data: ByteArray = byteArrayOf()
    }

    /**
     * Full save data containing friend list, last seen DHT nodes, name, and all other information
     * contained within a Tox instance.
     */
    class ToxSave(
        override val data: ByteArray,
    ) : Type {
        override val kind: ToxSavedataType = ToxSavedataType.TOX_SAVE

        // `data class` over a `ByteArray` would use referential
        // equality; two equal save blobs would compare unequal.
        // Hand-write content-based `equals` / `hashCode`.
        // `toString` elides the payload — save data contains the
        // secret key and shouldn't land in logs.
        override fun equals(other: Any?): Boolean = this === other || (other is ToxSave && data.contentEquals(other.data))

        override fun hashCode(): Int = data.contentHashCode()

        override fun toString(): String = "ToxSave(data=<${data.size} bytes>)"
    }

    /**
     * Minimal save data with just the secret key. The public key can be derived from it. Saving
     * this secret key, the friend list, name, and noSpam value is sufficient to restore the
     * observable behaviour of a Tox instance without the full save data in [ToxSave].
     */
    class SecretKey(
        private val key: ToxSecretKey,
    ) : Type {
        override val kind: ToxSavedataType = ToxSavedataType.SECRET_KEY
        override val data: ByteArray = key.value

        // Same ByteArray-equality footgun as `ToxSave`; compare
        // on the underlying secret-key bytes. `toString` omits
        // the key.
        override fun equals(other: Any?): Boolean = this === other || (other is SecretKey && data.contentEquals(other.data))

        override fun hashCode(): Int = data.contentHashCode()

        override fun toString(): String = "SecretKey(<${data.size} bytes>)"
    }
}
