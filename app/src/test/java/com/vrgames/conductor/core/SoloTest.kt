package com.vrgames.conductor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoloTest {
    @Test
    fun holdingAPlayingSectionGivesItTheSolo() {
        val p = Pieces.ALL.first()
        val perf = Performance(p)
        val s = p.sections.first().id
        val other = p.sections[1].id
        // Bring it in (out of the cues: free entry), let go, then hold it again.
        perf.advance(0.0, 0.0)
        perf.pinch(s); perf.letGo(s)
        perf.pinch(s)
        perf.advance(0.3, 0.3)
        assertNull("not yet: a pinch is not a solo", perf.solo())
        perf.advance(0.7, 0.4)
        assertEquals(s, perf.solo())
        assertEquals(PerfConst.UP, perf.soloFactor(s), 1e-9)
        assertEquals(PerfConst.DOWN, perf.soloFactor(other), 1e-9)
        // Fingers part: back to the ensemble.
        perf.letGo(s)
        perf.advance(0.8, 0.1)
        assertNull(perf.solo())
        assertEquals(1.0, perf.soloFactor(other), 1e-9)
    }

    @Test
    fun aSilentSectionHeldIsNoSolo() {
        val p = Pieces.ALL.first()
        val perf = Performance(p)
        perf.advance(0.0, 0.0)
        assertNull(perf.solo())
    }

    private object PerfConst {
        val UP = Performance.SOLO_UP
        val DOWN = Performance.SOLO_DOWN
    }
}
