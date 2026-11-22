package im.tox.tox4j.exceptions

/**
 * Exception to be thrown when a method is invoked on a tox instance that has been closed.
 *
 * @author Simon Levermann (sonOfRa)
 */
class ToxKilledException(
    message: String,
) : RuntimeException(message)
