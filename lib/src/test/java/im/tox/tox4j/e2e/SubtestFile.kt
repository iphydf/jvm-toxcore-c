package im.tox.tox4j.e2e

import im.tox.tox4j.core.ToxCoreConstants
import im.tox.tox4j.core.ToxCoreEvent
import im.tox.tox4j.core.data.ToxFileId
import im.tox.tox4j.core.data.ToxFileNumber
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.enums.ToxFileControl
import im.tox.tox4j.core.enums.ToxFileKind
import kotlin.test.assertContentEquals

@OptIn(kotlin.ExperimentalStdlibApi::class)
object SubtestFile {
    private val payload = "Hello File Transfer World".encodeToByteArray()

    fun run(harness: Tox4jHarness) {
        check(harness.toxes.size >= 2) { "SubtestFile needs at least 2 toxes" }
        val alice = harness.toxes[0]
        val bob = harness.toxes[1]
        val bobFromAlice = alice.friendByPublicKey(bob.publicKey)
        val aliceFromBob = bob.friendByPublicKey(alice.publicKey)

        val aliceFileNumber =
            alice.fileSend(
                bobFromAlice,
                ToxFileKind.DATA,
                payload.size.toLong(),
                ToxFileId(ByteArray(ToxCoreConstants.FILE_ID_LENGTH)),
                "test.txt".encodeToByteArray(),
            )

        data class State(
            val bobFileNumber: ToxFileNumber? = null,
            val received: ByteArray = ByteArray(0),
            val completed: Boolean = false,
        )

        val handler =
            { state: State, event: ToxCoreEvent ->
                when (event) {
                    is ToxCoreEvent.FileRecv -> {
                        with(event) {
                            if (state.bobFileNumber == null && friendNumber == aliceFromBob) {
                                bob.fileControl(friendNumber, fileNumber, ToxFileControl.RESUME)
                                state.copy(bobFileNumber = fileNumber)
                            } else {
                                state
                            }
                        }
                    }

                    is ToxCoreEvent.FileRecvChunk -> {
                        with(event) {
                            if (friendNumber == aliceFromBob && fileNumber == state.bobFileNumber) {
                                if (data.isEmpty()) {
                                    state.copy(completed = true)
                                } else {
                                    state.copy(received = state.received + data)
                                }
                            } else {
                                state
                            }
                        }
                    }

                    is ToxCoreEvent.FileChunkRequest -> {
                        with(event) {
                            if (friendNumber == bobFromAlice && fileNumber == aliceFileNumber) {
                                val from = position.toInt()
                                val len = length.toInt()
                                if (len == 0 || from >= payload.size) {
                                    alice.fileSendChunk(friendNumber, fileNumber, position, ByteArray(0))
                                } else {
                                    val end = minOf(from + len, payload.size)
                                    alice.fileSendChunk(friendNumber, fileNumber, position, payload.copyOfRange(from, end))
                                }
                            }
                            state
                        }
                    }

                    else -> {
                        state
                    }
                }
            }

        val finished =
            harness.waitFor(
                initial = State(),
                handler = handler,
                timeoutMs = 30_000L,
                message = "File transfer did not complete within timeout",
            ) { it.completed }

        assertContentEquals(payload, finished.received)
    }
}
