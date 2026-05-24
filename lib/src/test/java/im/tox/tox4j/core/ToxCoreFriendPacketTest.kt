package im.tox.tox4j.core

import im.tox.tox4j.core.data.ToxFriendLosslessPacket
import im.tox.tox4j.core.data.ToxFriendLossyPacket
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.data.ToxPublicKey
import im.tox.tox4j.core.data.ToxSavedata
import im.tox.tox4j.core.exceptions.ToxFriendCustomPacketException
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.impl.jni.ToxCoreImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Validation around custom packet IDs:
 *  - lossy first byte must be 192-254
 *  - lossless first byte must be 69 or 160-191
 *  - empty packets are rejected
 *  - oversize packets are rejected
 *
 * The friend isn't connected, so each test exercises only the per-call
 * input-validation path (which fires before FRIEND_NOT_CONNECTED).
 */
class ToxCoreFriendPacketTest {
    private val options = ToxOptions(localDiscoveryEnabled = false)

    private inline fun withFriend(block: (ToxCoreImpl, ToxFriendNumber) -> Unit) {
        ToxCoreImpl(options).use { peer ->
            ToxCoreImpl(options).use { tox ->
                val friend = tox.friendAddNorequest(peer.publicKey)
                block(tox, friend)
            }
        }
    }

    @Test
    fun lossyPacket_badFirstByte_throwsInvalid() {
        withFriend { tox, friend ->
            val ex =
                assertFailsWith<ToxFriendCustomPacketException> {
                    tox.friendSendLossyPacket(friend, ToxFriendLossyPacket(byteArrayOf(100, 1, 2, 3)))
                }
            assertEquals(ToxFriendCustomPacketException.Code.INVALID, ex.code)
        }
    }

    @Test
    fun lossyPacket_empty_throwsEmpty() {
        withFriend { tox, friend ->
            val ex =
                assertFailsWith<ToxFriendCustomPacketException> {
                    tox.friendSendLossyPacket(friend, ToxFriendLossyPacket(ByteArray(0)))
                }
            assertEquals(ToxFriendCustomPacketException.Code.EMPTY, ex.code)
        }
    }

    @Test
    fun lossyPacket_tooLong_throwsTooLong() {
        withFriend { tox, friend ->
            val data = ByteArray(ToxCoreConstants.MAX_CUSTOM_PACKET_SIZE + 1)
            data[0] = 200.toByte()
            val ex =
                assertFailsWith<ToxFriendCustomPacketException> {
                    tox.friendSendLossyPacket(friend, ToxFriendLossyPacket(data))
                }
            assertEquals(ToxFriendCustomPacketException.Code.TOO_LONG, ex.code)
        }
    }

    @Test
    fun lossyPacket_validRange_failsOnlyOnDisconnect() {
        // First byte 200 is valid; the friend isn't connected so the call
        // still fails — but with FRIEND_NOT_CONNECTED, not INVALID.
        withFriend { tox, friend ->
            val ex =
                assertFailsWith<ToxFriendCustomPacketException> {
                    tox.friendSendLossyPacket(friend, ToxFriendLossyPacket(byteArrayOf(200.toByte(), 1, 2)))
                }
            assertEquals(ToxFriendCustomPacketException.Code.FRIEND_NOT_CONNECTED, ex.code)
        }
    }

    @Test
    fun losslessPacket_badFirstByte_throwsInvalid() {
        withFriend { tox, friend ->
            val ex =
                assertFailsWith<ToxFriendCustomPacketException> {
                    tox.friendSendLosslessPacket(friend, ToxFriendLosslessPacket(byteArrayOf(100, 1, 2)))
                }
            assertEquals(ToxFriendCustomPacketException.Code.INVALID, ex.code)
        }
    }

    @Test
    fun losslessPacket_empty_throwsEmpty() {
        withFriend { tox, friend ->
            val ex =
                assertFailsWith<ToxFriendCustomPacketException> {
                    tox.friendSendLosslessPacket(friend, ToxFriendLosslessPacket(ByteArray(0)))
                }
            assertEquals(ToxFriendCustomPacketException.Code.EMPTY, ex.code)
        }
    }

    @Test
    fun losslessPacket_validRange_failsOnlyOnDisconnect() {
        // First byte 160 is valid lossless; expect FRIEND_NOT_CONNECTED.
        withFriend { tox, friend ->
            val ex =
                assertFailsWith<ToxFriendCustomPacketException> {
                    tox.friendSendLosslessPacket(friend, ToxFriendLosslessPacket(byteArrayOf(160.toByte(), 1, 2)))
                }
            assertEquals(ToxFriendCustomPacketException.Code.FRIEND_NOT_CONNECTED, ex.code)
        }
    }

    @Test
    fun friendList_threeFriends_orderPreserved() {
        ToxCoreImpl(options).use { peer1 ->
            ToxCoreImpl(options).use { peer2 ->
                ToxCoreImpl(options).use { peer3 ->
                    ToxCoreImpl(options).use { tox ->
                        val f1 = tox.friendAddNorequest(peer1.publicKey)
                        val f2 = tox.friendAddNorequest(peer2.publicKey)
                        val f3 = tox.friendAddNorequest(peer3.publicKey)
                        assertEquals(0, f1.value)
                        assertEquals(1, f2.value)
                        assertEquals(2, f3.value)
                        assertEquals(listOf(f1, f2, f3), tox.friendList)
                    }
                }
            }
        }
    }

    @Test
    fun friendList_deleteMiddle_reusesSlot() {
        // After deleting f1 from {0,1,2}, the next add should land on slot 1
        // (Tox reuses freed slots) and the list should be {0,1,2} again.
        ToxCoreImpl(options).use { peer1 ->
            ToxCoreImpl(options).use { peer2 ->
                ToxCoreImpl(options).use { peer3 ->
                    ToxCoreImpl(options).use { peer4 ->
                        ToxCoreImpl(options).use { tox ->
                            tox.friendAddNorequest(peer1.publicKey)
                            val f1 = tox.friendAddNorequest(peer2.publicKey)
                            tox.friendAddNorequest(peer3.publicKey)
                            tox.friendDelete(f1)
                            val f4 = tox.friendAddNorequest(peer4.publicKey)
                            assertEquals(1, f4.value, "Slot 1 should be reused after delete")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun savedata_preservesFriends() {
        val savedata: ToxSavedata
        val expectedFriendKey: ByteArray
        ToxCoreImpl(options).use { peer ->
            expectedFriendKey = peer.publicKey.value
            ToxCoreImpl(options).use { tox ->
                tox.friendAddNorequest(ToxPublicKey(expectedFriendKey))
                savedata = tox.savedata
            }
        }
        // Reload into a fresh Tox; the friend must still be there.
        ToxCoreImpl(
            options.copy(
                saveData =
                    im.tox.tox4j.core.options.SaveDataOptions
                        .ToxSave(savedata.value),
            ),
        ).use { tox ->
            assertEquals(
                listOf(
                    im.tox.tox4j.core.data
                        .ToxFriendNumber(0),
                ),
                tox.friendList,
            )
            kotlin.test.assertContentEquals(
                expectedFriendKey,
                tox
                    .friendGetPublicKey(
                        im.tox.tox4j.core.data
                            .ToxFriendNumber(0),
                    ).value,
            )
        }
    }
}
