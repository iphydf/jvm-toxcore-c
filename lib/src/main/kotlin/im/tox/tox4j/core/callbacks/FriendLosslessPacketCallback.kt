package im.tox.tox4j.core.callbacks

import im.tox.tox4j.core.data.ToxFriendLosslessPacket
import im.tox.tox4j.core.data.ToxFriendNumber

interface FriendLosslessPacketCallback<ToxCoreState> {
    /**
     * @param friendNumber The friend number of the friend who sent the packet.
     * @param data A byte array containing the received packet data.
     */
    fun friendLosslessPacket(
        friendNumber: ToxFriendNumber,
        data: ToxFriendLosslessPacket,
        state: ToxCoreState,
    ): ToxCoreState = state
}
