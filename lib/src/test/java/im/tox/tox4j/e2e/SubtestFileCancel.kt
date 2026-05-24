package im.tox.tox4j.e2e

import im.tox.tox4j.core.ToxCoreConstants
import im.tox.tox4j.core.callbacks.ToxCoreEventListener
import im.tox.tox4j.core.data.ToxFileId
import im.tox.tox4j.core.data.ToxFileNumber
import im.tox.tox4j.core.data.ToxFilename
import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.enums.ToxFileControl
import im.tox.tox4j.core.enums.ToxFileKind
import kotlin.test.assertEquals

/**
 * File-transfer cancel flow. Alice starts a transfer, Bob accepts with
 * RESUME, Alice immediately cancels — Bob's `fileRecvControl` callback
 * must fire with `CANCEL`. Mirrors the cancel-half of
 * `rs-toxcore-c/toxcore/tests/suite/file.rs::subtest_file_transfer`.
 */
@OptIn(kotlin.ExperimentalStdlibApi::class)
object SubtestFileCancel {
    fun run(harness: Tox4jHarness) {
        check(harness.toxes.size >= 2) { "SubtestFileCancel needs at least 2 toxes" }
        val alice = harness.toxes[0]
        val bob = harness.toxes[1]
        val bobFromAlice = alice.friendByPublicKey(bob.publicKey)
        val aliceFromBob = bob.friendByPublicKey(alice.publicKey)

        val aliceFileNumber =
            alice.fileSend(
                bobFromAlice,
                ToxFileKind.DATA,
                8L,
                ToxFileId(ByteArray(ToxCoreConstants.FILE_ID_LENGTH)),
                ToxFilename("cancel.bin".encodeToByteArray()),
            )

        data class State(
            val bobFileNumber: ToxFileNumber? = null,
            val canceled: Boolean = false,
        )

        val handler =
            object : ToxCoreEventListener<State> {
                override fun fileRecv(
                    friendNumber: ToxFriendNumber,
                    fileNumber: ToxFileNumber,
                    kind: Int,
                    fileSize: Long,
                    filename: ToxFilename,
                    state: State,
                ): State =
                    if (state.bobFileNumber == null && friendNumber.value == aliceFromBob.value) {
                        bob.fileControl(friendNumber, fileNumber, ToxFileControl.RESUME)
                        state.copy(bobFileNumber = fileNumber)
                    } else {
                        state
                    }

                override fun fileRecvControl(
                    friendNumber: ToxFriendNumber,
                    fileNumber: ToxFileNumber,
                    control: ToxFileControl,
                    state: State,
                ): State =
                    if (state.bobFileNumber != null &&
                        friendNumber.value == aliceFromBob.value &&
                        fileNumber == state.bobFileNumber &&
                        control == ToxFileControl.CANCEL
                    ) {
                        state.copy(canceled = true)
                    } else {
                        state
                    }
            }

        val finished =
            harness.waitFor(
                initial = State(),
                handler = handler,
                timeoutMs = 30_000L,
                message = "Bob never received the file CANCEL control event",
            ) { state ->
                // As soon as Bob has accepted (bobFileNumber set), Alice sends
                // CANCEL. Retrying is idempotent — once the file is gone the
                // second call throws NOT_FOUND, which we silently swallow.
                if (state.bobFileNumber != null) {
                    try {
                        alice.fileControl(bobFromAlice, aliceFileNumber, ToxFileControl.CANCEL)
                    } catch (_: Exception) {
                        // ignored: transfer already cancelled or not yet active
                    }
                }
                state.canceled
            }
        assertEquals(true, finished.canceled)
    }
}
