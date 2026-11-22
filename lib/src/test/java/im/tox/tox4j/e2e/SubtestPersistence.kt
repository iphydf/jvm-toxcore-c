package im.tox.tox4j.e2e

import im.tox.tox4j.core.data.ToxPublicKey
import im.tox.tox4j.core.options.SavedataOptions
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.impl.jni.ToxCoreImpl
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@OptIn(kotlin.ExperimentalStdlibApi::class)
object SubtestPersistence {
    fun run(
        @Suppress("UNUSED_PARAMETER") harness: Tox4jHarness,
    ) {
        val baseOptions = ToxOptions(localDiscoveryEnabled = false, ipv6Enabled = false)
        val savedata: ByteArray
        val publicKey: ToxPublicKey
        ToxCoreImpl(baseOptions).use { tox ->
            tox.setName("PersistentUser".encodeToByteArray())
            tox.setStatusMessage("I will be back".encodeToByteArray())
            publicKey = tox.publicKey
            savedata = tox.savedata
        }

        ToxCoreImpl(baseOptions.copy(savedata = SavedataOptions.ToxSave(savedata))).use { tox ->
            assertEquals(publicKey, tox.publicKey)
            assertContentEquals("PersistentUser".encodeToByteArray(), tox.name)
            assertContentEquals("I will be back".encodeToByteArray(), tox.statusMessage)
        }
    }
}
