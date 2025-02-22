package im.tox.tox4j.core.callbacks

import im.tox.tox4j.core.data.ToxFilename
import im.tox.tox4j.core.data.ToxFriendNumber

/** This event is triggered when a file transfer request is received. */
interface FileRecvCallback<ToxCoreState> {
    /**
     * The client should acquire resources to be associated with the file transfer. Incoming file
     * transfers start in the PAUSED state. After this callback returns, a transfer can be rejected
     * by sending a TOX_FILE_CONTROL_CANCEL control command before any other control commands. It
     * can be accepted by sending TOX_FILE_CONTROL_RESUME.
     *
     * @param friendNumber The friend number of the friend who is sending the file transfer request.
     * @param fileNumber The friend-specific file number the data received is associated with.
     * @param kind The meaning of the file that was sent.
     * @param fileSize Size in bytes of the file the client wants to send, UINT64_MAX if unknown or
     *   streaming.
     * @param filename Name of the file. Does not need to be the actual name. This name will be sent
     *   along with the file send request.
     */
    fun fileRecv(
        friendNumber: ToxFriendNumber,
        fileNumber: Int,
        kind: Int,
        fileSize: Long,
        filename: ToxFilename,
        state: ToxCoreState,
    ): ToxCoreState = state
}
