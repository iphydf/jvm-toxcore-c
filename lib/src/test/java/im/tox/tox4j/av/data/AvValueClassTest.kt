package im.tox.tox4j.av.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Boundary checks on the AV value-class wrappers. The C side types
 * `width`/`height` as `uint16_t` and `sample_count` as `size_t`;
 * Kotlin holds them as `Int`. Validation catches out-of-range
 * values up front so they can't silently truncate at the JNI
 * boundary.
 */
class AvValueClassTest {
    @Test
    fun width_accepts_zero_and_max_uint16() {
        Width(0)
        Width(Width.MAX)
    }

    @Test
    fun width_rejects_negative() {
        assertFailsWith<IllegalArgumentException> { Width(-1) }
    }

    @Test
    fun width_rejects_above_uint16() {
        assertFailsWith<IllegalArgumentException> { Width(Width.MAX + 1) }
    }

    @Test
    fun height_accepts_zero_and_max_uint16() {
        Height(0)
        Height(Height.MAX)
    }

    @Test
    fun height_rejects_negative() {
        assertFailsWith<IllegalArgumentException> { Height(-1) }
    }

    @Test
    fun height_rejects_above_uint16() {
        assertFailsWith<IllegalArgumentException> { Height(Height.MAX + 1) }
    }

    @Test
    fun sampleCount_accepts_zero() {
        SampleCount(0)
    }

    @Test
    fun sampleCount_rejects_negative() {
        assertFailsWith<IllegalArgumentException> { SampleCount(-1) }
    }

    @Test
    fun sampleCount_derives_from_audioLength_and_rate() {
        // 10ms at 48kHz = 480 samples.
        assertEquals(480, SampleCount(AudioLength.Length10, SamplingRate.Rate48k).value)
    }

    // The coercing factories pin a different contract from the
    // strict constructor: out-of-range input clamps to a valid value
    // instead of throwing. The dispatcher relies on this so a
    // malformed proto field doesn't drop the rest of an event batch.

    @Test
    fun width_fromInt_clamps_negative_to_zero() {
        assertEquals(0, Width.fromInt(-1).value)
        assertEquals(0, Width.fromInt(Int.MIN_VALUE).value)
    }

    @Test
    fun width_fromInt_clamps_above_max_to_max() {
        assertEquals(Width.MAX, Width.fromInt(Width.MAX + 1).value)
        assertEquals(Width.MAX, Width.fromInt(Int.MAX_VALUE).value)
    }

    @Test
    fun width_fromInt_is_identity_in_range() {
        assertEquals(0, Width.fromInt(0).value)
        assertEquals(640, Width.fromInt(640).value)
        assertEquals(Width.MAX, Width.fromInt(Width.MAX).value)
    }

    @Test
    fun height_fromInt_clamps_out_of_range() {
        assertEquals(0, Height.fromInt(-1).value)
        assertEquals(Height.MAX, Height.fromInt(Height.MAX + 1).value)
        assertEquals(480, Height.fromInt(480).value)
    }

    @Test
    fun sampleCount_fromInt_clamps_negative_to_zero() {
        assertEquals(0, SampleCount.fromInt(-1).value)
        assertEquals(0, SampleCount.fromInt(Int.MIN_VALUE).value)
        assertEquals(480, SampleCount.fromInt(480).value)
    }
}
