package im.tox.tox4j.core.callbacks

import im.tox.tox4j.core.data.ToxFriendNumber
import im.tox.tox4j.core.data.ToxGroupInviteData
import im.tox.tox4j.core.data.ToxGroupName

interface GroupInviteCallback<ToxCoreState> {
    /**
     * @param friendNumber The friend number of the contact who sent the invite.
     * @param inviteData The invite data.
     * @param groupName The name of the group. In conferences, this is "title".
     */
    fun groupInvite(
        friendNumber: ToxFriendNumber,
        inviteData: ToxGroupInviteData,
        groupName: ToxGroupName,
        state: ToxCoreState,
    ): ToxCoreState = state
}
