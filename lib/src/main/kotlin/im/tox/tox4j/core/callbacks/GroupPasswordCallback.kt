package im.tox.tox4j.core.callbacks

import im.tox.tox4j.core.data.ToxGroupNumber
import im.tox.tox4j.core.data.ToxGroupPassword

interface GroupPasswordCallback<ToxCoreState> {
    /**
     * @param groupNumber The group number of the group for which the password has changed.
     * @param password The new group password.
     */
    fun groupPassword(
        groupNumber: ToxGroupNumber,
        password: ToxGroupPassword,
        state: ToxCoreState,
    ): ToxCoreState = state
}
