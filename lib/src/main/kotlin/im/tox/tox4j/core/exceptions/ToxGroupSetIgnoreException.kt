package im.tox.tox4j.core.exceptions

import im.tox.tox4j.exceptions.ToxException

class ToxGroupSetIgnoreException(
    override val code: Code,
) : ToxException(code) {
    enum class Code {
        /** The group number passed did not designate a valid group. */
        GROUP_NOT_FOUND,

        /** The ID passed did not designate a valid peer. */
        PEER_NOT_FOUND,

        /** The caller attempted to ignore himself. */
        SELF,
    }
}
