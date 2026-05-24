package im.tox.tox4j.core

import im.tox.tox4j.core.data.ToxConferenceId
import im.tox.tox4j.core.data.ToxConferenceNumber
import im.tox.tox4j.core.data.ToxConferenceTitle
import im.tox.tox4j.core.enums.ToxConferenceType
import im.tox.tox4j.core.exceptions.ToxConferenceByIdException
import im.tox.tox4j.core.exceptions.ToxConferenceDeleteException
import im.tox.tox4j.core.exceptions.ToxConferenceGetTypeException
import im.tox.tox4j.core.exceptions.ToxConferenceTitleException
import im.tox.tox4j.core.options.ToxOptions
import im.tox.tox4j.impl.jni.ToxCoreImpl
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Single-tox conference operations: lifecycle, title, id, type, and the
 * error paths for queries on unknown conference numbers. The error-path
 * tests are the regression coverage for the
 * "out-param read before error check" bug we fixed in
 * `ToxInstances.h::with_error_handling` — pre-fix, these calls crashed
 * the JVM under UBSan; post-fix, they throw the matching Tox exception.
 */
@OptIn(kotlin.ExperimentalStdlibApi::class)
class ToxCoreConferenceTest {
    private val options = ToxOptions(localDiscoveryEnabled = false)

    @Test
    fun new_returnsZeroOnFirst() {
        ToxCoreImpl(options).use { tox ->
            assertEquals(0, tox.conferenceNew().value)
        }
    }

    @Test
    fun new_thenDelete_succeeds() {
        ToxCoreImpl(options).use { tox ->
            val conf = tox.conferenceNew()
            tox.conferenceDelete(conf)
        }
    }

    @Test
    fun chatlist_emptyInitially() {
        ToxCoreImpl(options).use { tox ->
            assertEquals(0, tox.conferenceGetChatlist.size)
        }
    }

    @Test
    fun chatlist_reflectsActiveConferences() {
        ToxCoreImpl(options).use { tox ->
            val a = tox.conferenceNew()
            val b = tox.conferenceNew()
            assertEquals(
                setOf(a, b),
                tox.conferenceGetChatlist.toSet(),
            )
            tox.conferenceDelete(a)
            assertEquals(listOf(b), tox.conferenceGetChatlist)
        }
    }

    @Test
    fun newTwo_distinctNumbers() {
        ToxCoreImpl(options).use { tox ->
            val a = tox.conferenceNew()
            val b = tox.conferenceNew()
            assertEquals(0, a.value)
            assertEquals(1, b.value)
        }
    }

    @Test
    fun reuseNumber_afterDelete() {
        ToxCoreImpl(options).use { tox ->
            val a = tox.conferenceNew()
            tox.conferenceDelete(a)
            val b = tox.conferenceNew()
            assertEquals(0, b.value)
        }
    }

    // --- Error paths on unknown conference numbers -----------------------
    //
    // Pre-fix to `with_error_handling`, every one of these crashed the JVM
    // with SIGILL (the C function returns a sentinel `(Tox_Conference_Type)-1`
    // and UBSan's enum-range-check trapped on the raw return). Post-fix the
    // raw return is only converted on the success path, so the error code
    // is observed first and a proper Tox exception is thrown.

    @Test
    fun getType_unknownConference_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxConferenceGetTypeException> {
                    tox.conferenceGetType(ToxConferenceNumber(999))
                }
            assertEquals(ToxConferenceGetTypeException.Code.CONFERENCE_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun getTitle_beforeSet_throwsInvalidLength() {
        // A fresh conference has no title; `tox_conference_get_title_size`
        // returns 0, which c-toxcore reports as TOX_ERR_CONFERENCE_TITLE_INVALID_LENGTH.
        ToxCoreImpl(options).use { tox ->
            val conf = tox.conferenceNew()
            val ex =
                assertFailsWith<ToxConferenceTitleException> {
                    tox.conferenceGetTitle(conf)
                }
            assertEquals(ToxConferenceTitleException.Code.INVALID_LENGTH, ex.code)
        }
    }

    @Test
    fun setTitle_thenGetTitle_roundtrip() {
        ToxCoreImpl(options).use { tox ->
            val conf = tox.conferenceNew()
            val title = ToxConferenceTitle("My Chat".encodeToByteArray())
            tox.conferenceSetTitle(conf, title)
            assertContentEquals(title.value, tox.conferenceGetTitle(conf).value)
        }
    }

    @Test
    fun setTitle_unknownConference_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxConferenceTitleException> {
                    tox.conferenceSetTitle(
                        ToxConferenceNumber(999),
                        ToxConferenceTitle("x".encodeToByteArray()),
                    )
                }
            assertEquals(ToxConferenceTitleException.Code.CONFERENCE_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun delete_unknown_throws() {
        ToxCoreImpl(options).use { tox ->
            val ex =
                assertFailsWith<ToxConferenceDeleteException> {
                    tox.conferenceDelete(ToxConferenceNumber(999))
                }
            assertEquals(ToxConferenceDeleteException.Code.CONFERENCE_NOT_FOUND, ex.code)
        }
    }

    @Test
    fun getId_returnsExpectedLength() {
        ToxCoreImpl(options).use { tox ->
            val conf = tox.conferenceNew()
            assertEquals(32, tox.conferenceGetId(conf).value.size)
        }
    }

    @Test
    fun conferenceById_unknown_throws() {
        ToxCoreImpl(options).use { tox ->
            val bogus = ToxConferenceId(ByteArray(32))
            val ex =
                assertFailsWith<ToxConferenceByIdException> {
                    tox.conferenceById(bogus)
                }
            assertEquals(ToxConferenceByIdException.Code.NOT_FOUND, ex.code)
        }
    }

    @Test
    fun getType_defaultIsText() {
        ToxCoreImpl(options).use { tox ->
            val conf = tox.conferenceNew()
            assertEquals(ToxConferenceType.TEXT, tox.conferenceGetType(conf))
        }
    }
}
