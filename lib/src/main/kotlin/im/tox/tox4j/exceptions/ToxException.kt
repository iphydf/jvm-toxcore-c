package im.tox.tox4j.exceptions

/** Each subclass has its own `Code` enum, so `when (e.code)` can cover every code. */
abstract class ToxException(
    code: Enum<*>,
) : Exception("Error code: ${code.name}") {
    abstract val code: Enum<*>
}
