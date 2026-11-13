package com.vrgames.conductor.core

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos

class BeatHandTest {
    @Test
    fun aConductorsStrokesAreBeats() {
        val h = BeatHand()
        var beats = 0
        // Ten strokes at one a second, 12 cm deep, sampled at 72 Hz.
        var t = 0.0
        while (t < 10.0) { if (h.feed(t, 1.2 + 0.06 * cos(2 * Math.PI * t))) beats++; t += 1.0 / 72 }
        assertEquals(10.0, beats.toDouble(), 1.0)
    }

    @Test
    fun aStillOrTremblingHandGivesNoBeats() {
        val h = BeatHand()
        var beats = 0
        var t = 0.0
        while (t < 10.0) { if (h.feed(t, 1.2 + 0.01 * cos(2 * Math.PI * 3 * t))) beats++; t += 1.0 / 72 }
        assertEquals(0, beats)
    }
}
