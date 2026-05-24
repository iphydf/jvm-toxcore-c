package im.tox.tox4j.e2e

import im.tox.tox4j.core.data.ToxName
import im.tox.tox4j.core.data.ToxStatusMessage
import im.tox.tox4j.core.options.SaveDataOptions
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.impl.jni.ToxCoreImpl
import kotlin.test.assertContentEquals

/**
 * Savedata round-trip: a tox writes name/statusMessage/publicKey, exports
 * its savedata, exits; a fresh tox loaded from that savedata reports
 * identical fields. This subtest is self-contained — it builds its own
 * toxes rather than using the harness — but lives in the integration suite
 * to mirror `rs-toxcore-c/toxcore/tests/suite/persistence.rs`.
 */
@OptIn(kotlin.ExperimentalStdlibApi::class)
object SubtestPersistence {
    fun run(
        @Suppress("UNUSED_PARAMETER") harness: Tox4jHarness,
    ) {
        val baseOptions = ToxOptions(localDiscoveryEnabled = false, ipv6Enabled = false)
        val savedata: ByteArray
        val publicKey: ByteArray
        ToxCoreImpl(baseOptions).use { tox ->
            tox.setName(ToxName("PersistentUser".encodeToByteArray()))
            tox.setStatusMessage(ToxStatusMessage("I will be back".encodeToByteArray()))
            publicKey = tox.publicKey.value
            savedata = tox.savedata
        }

        ToxCoreImpl(baseOptions.copy(saveData = SaveDataOptions.ToxSave(savedata))).use { tox ->
            assertContentEquals(publicKey, tox.publicKey.value)
            assertContentEquals("PersistentUser".encodeToByteArray(), tox.name.value)
            assertContentEquals("I will be back".encodeToByteArray(), tox.statusMessage.value)
        }
    }
}
