package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxConferencePeerQueryException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** The conference number passed did not designate a valid conference. */
        CONFERENCE_NOT_FOUND,

        /** The peer number passed did not designate a valid peer. */
        PEER_NOT_FOUND,

        /** The client is not connected to the conference. */
        NO_CONNECTION,
    }
}
