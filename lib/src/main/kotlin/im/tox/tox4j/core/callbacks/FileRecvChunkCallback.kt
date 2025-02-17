package im.tox.tox4j.core.callbacks

import im.tox.tox4j.core.data.ToxFriendNumber

/**
 * This event is first triggered when a file transfer request is received, and subsequently when a
 * chunk of file data for an accepted request was received.
 */
interface FileRecvChunkCallback<ToxCoreState> {
    /**
     * When length is 0, the transfer is finished and the client should release the resources it
     * acquired for the transfer. After a call with length = 0, the file number can be reused for
     * new file transfers.
     *
     * If position is equal to file_size (received in the file_receive callback) when the transfer
     * finishes, the file was received completely. Otherwise, if file_size was UINT64_MAX, streaming
     * ended successfully when length is 0.
     *
     * @param friendNumber The friend number of the friend who is sending the file.
     * @param fileNumber The friend-specific file number the data received is associated with.
     * @param position The file position of the first byte in data.
     * @param data A byte array containing the received chunk.
     */
    fun fileRecvChunk(
        friendNumber: ToxFriendNumber,
        fileNumber: Int,
        position: Long,
        data: ByteArray,
        state: ToxCoreState,
    ): ToxCoreState = state
}
