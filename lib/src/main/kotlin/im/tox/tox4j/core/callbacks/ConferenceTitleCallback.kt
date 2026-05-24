package im.tox.tox4j.core.callbacks

import im.tox.tox4j.core.data.ToxConferenceNumber
import im.tox.tox4j.core.data.ToxConferencePeerNumber
import im.tox.tox4j.core.data.ToxConferenceTitle

interface ConferenceTitleCallback<ToxCoreState> {
    /**
     * @param conferenceNumber The conference number of the conference the title change is intended for.
     * @param peerNumber The ID of the peer who changed the title.
     * @param title The title data.
     */
    fun conferenceTitle(
        conferenceNumber: ToxConferenceNumber,
        peerNumber: ToxConferencePeerNumber,
        title: ToxConferenceTitle,
        state: ToxCoreState,
    ): ToxCoreState = state
}
