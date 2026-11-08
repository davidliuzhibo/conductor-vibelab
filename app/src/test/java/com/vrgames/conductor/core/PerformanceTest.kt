package com.vrgames.conductor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceTest {
    private fun run(perf: Performance, each: (Performance) -> Unit = {}) {
        val dt = 1.0 / 30
        var t = perf.time
        while (!perf.finished) {
            t += dt
            perf.advance(t, dt)
            each(perf)
        }
    }

    @Test
    fun everyPieceIsWellFormed() {
        assertEquals(8, Pieces.ALL.size)
        for (p in Pieces.ALL) {
            assertEquals(p.id, 4, p.sections.size)
            val ids = p.sections.map { it.id }
            assertTrue(p.id, p.cues.isNotEmpty())
            for (c in p.cues) {
                assertTrue("${p.id} $c", c.section in ids)
                assertTrue("${p.id} $c", p.time(c.beat) < p.seconds)
            }
            // Every section is brought in at some point, or the piece would leave one silent for ever.
            for (id in ids) assertTrue("${p.id} never brings in $id", p.cues.any { it is Cue.Enter && it.section == id })
            // A release can only stop what has been brought in before it.
            for (c in p.cues.filterIsInstance<Cue.Release>()) assertTrue("${p.id} $c", p.cues.any { it is Cue.Enter && it.section == c.section && it.beat < c.beat })
            for (c in p.cues.filterIsInstance<Cue.Swell>()) {
                assertTrue("${p.id} $c", c.endBeat > c.beat)
                assertTrue("${p.id} $c", c.from in 0.0..1.0 && c.to in 0.0..1.0)
            }
        }
    }

    @Test
    fun aPerfectRunEarnsThreeStarsOnEveryPiece() {
        for (p in Pieces.ALL) {
            val perf = Performance(p)
            val bot = Bot(perf)
            run(perf) { bot.act() }
            assertEquals(p.id, 3, perf.stars())
            assertTrue("${p.id} ${perf.accuracy()}", perf.accuracy() > 0.9)
        }
    }

    @Test
    fun doingNothingEarnsNothingAndTheMusicStillEnds() {
        for (p in Pieces.ALL) {
            val perf = Performance(p)
            run(perf)
            assertEquals(p.id, 0, perf.stars())
            assertTrue(perf.finished)
            for (s in p.sections) assertEquals(0.0, perf.gain(s.id), 1e-9)
        }
    }

    @Test
    fun timingDecidesTheGrade() {
        val p = Pieces.ALL[0]
        val cue = p.cues.first() as Cue.Enter
        fun at(offset: Double): Ev.Entered {
            val perf = Performance(p)
            perf.advance(p.time(cue.beat) + offset, 0.0)
            perf.pinch(cue.section)
            return perf.drain().filterIsInstance<Ev.Entered>().single()
        }
        assertEquals(Grade.PERFECT, at(0.05).grade)
        assertEquals(Grade.PERFECT, at(-0.3).grade)
        // The window leans late: reacting to the bright light plus hand-tracking delay still counts.
        assertEquals(Grade.PERFECT, at(0.3).grade)
        assertEquals(Grade.GOOD, at(-0.6).grade)
        assertEquals(Grade.GOOD, at(0.6).grade)
    }

    @Test
    fun aPinchWhileTheLightIsStillDimSaysEarlyAndSpendsNothing() {
        val p = Pieces.ALL[0]
        val cue = p.cues.first() as Cue.Enter
        val perf = Performance(p)
        perf.advance(p.time(cue.beat) - 2.0, 0.0)
        assertNotNull("the light is on (get ready)", perf.wants(cue.section))
        assertNull("but not yet bright", perf.due(cue.section))
        perf.pinch(cue.section)
        assertTrue(perf.drain().any { it is Ev.Early })
        assertFalse("the section stays silent", perf.isOn(cue.section))
        perf.letGo(cue.section)
        perf.advance(p.time(cue.beat) - 0.1, 0.0)
        assertNotNull("now it is bright", perf.due(cue.section))
        perf.pinch(cue.section)
        assertEquals(Grade.PERFECT, perf.drain().filterIsInstance<Ev.Entered>().single().grade)
    }

    @Test
    fun aPlayerWhoPinchesHalfASecondAfterTheLightTurnsBrightScoresWell() {
        // A human: waits for the bright light, then needs about half a second (seeing, deciding, the hand, tracking).
        for (p in Pieces.ALL) {
            val perf = Performance(p)
            val seen = HashMap<Cue, Double>()
            var t = perf.time
            val dt = 1.0 / 72
            while (!perf.finished) {
                for (s in p.sections) {
                    val c = perf.due(s.id) ?: continue
                    val first = seen.getOrPut(c) { t }
                    if (t - first >= 0.5) when (c) {
                        is Cue.Enter -> if (!perf.isOn(s.id)) { perf.pinch(s.id); perf.letGo(s.id) }
                        is Cue.Release -> { perf.pinch(s.id); perf.letGo(s.id) }
                        is Cue.Swell -> Unit
                    }
                }
                t += dt
                perf.advance(t, dt)
                perf.drain()
            }
            val entries = p.cues.count { it !is Cue.Swell }
            assertTrue("${p.id} ${perf.accuracy()}", perf.accuracy() * p.cues.size / entries > 0.85)
        }
    }

    @Test
    fun aLateEntryStillBringsTheSectionInButScoresNothing() {
        val p = Pieces.ALL[0]
        val cue = p.cues.first() as Cue.Enter
        val perf = Performance(p)
        perf.advance(p.time(cue.beat) + 2.0, 0.1)
        assertTrue(perf.drain().any { it is Ev.Missed })
        perf.pinch(cue.section)
        assertNull(perf.drain().filterIsInstance<Ev.Entered>().single().grade)
        assertTrue(perf.isOn(cue.section))
        assertEquals(0.0, perf.accuracy(), 1e-9)
    }

    @Test
    fun aSectionIsSilentUntilBroughtInAndFadesInWithoutAClick() {
        val p = Pieces.ALL[0]
        val cue = p.cues.first() as Cue.Enter
        val perf = Performance(p)
        perf.advance(p.time(cue.beat), 0.02)
        assertEquals(0.0, perf.gain(cue.section), 1e-9)
        perf.pinch(cue.section)
        perf.advance(p.time(cue.beat) + 0.02, 0.02)
        assertTrue(perf.gain(cue.section) in 0.01..0.2)
        perf.advance(p.time(cue.beat) + 1.0, 1.0)
        assertEquals(Performance.DEFAULT_LEVEL, perf.gain(cue.section), 1e-9)
    }

    @Test
    fun aSwellIsScoredByHowCloselyTheHandFollows() {
        val p = Pieces.ALL.first { it.cues.any { c -> c is Cue.Swell } }
        val swell = p.cues.filterIsInstance<Cue.Swell>().first()
        fun score(height: (Performance) -> Double): Double {
            val perf = Performance(p)
            var result = -1.0
            var t = perf.time
            val dt = 1.0 / 30
            while (t < p.time(swell.endBeat) + 0.1) {
                t += dt
                if (t >= p.time(swell.beat) && !perf.isHolding(swell.section)) perf.pinch(swell.section)
                perf.hold(swell.section, height(perf))
                perf.advance(t, dt)
                perf.drain().filterIsInstance<Ev.SwellDone>().forEach { result = it.score }
            }
            return result
        }
        val good = score { it.swellTarget(swell.section) ?: swell.from }
        val bad = score { if (swell.to > swell.from) 0.0 else 1.0 }
        assertTrue("good $good", good > 0.9)
        assertTrue("bad $bad", bad < 0.4)
    }

    @Test
    fun letGoStopsASectionOnlyWhenAReleaseIsDue() {
        val p = Pieces.ALL.first { it.cues.any { c -> c is Cue.Release } }
        val r = p.cues.filterIsInstance<Cue.Release>().first()
        val enter = p.cues.filterIsInstance<Cue.Enter>().first { it.section == r.section && it.beat < r.beat }
        val perf = Performance(p)
        perf.advance(p.time(enter.beat), 0.0)
        perf.pinch(r.section)
        perf.letGo(r.section)
        assertTrue(perf.isOn(r.section))
        perf.advance(p.time(r.beat), 0.0)
        perf.pinch(r.section)
        perf.letGo(r.section)
        assertFalse(perf.isOn(r.section))
        assertEquals(Grade.PERFECT, perf.drain().filterIsInstance<Ev.Released>().single().grade)
    }

    @Test
    fun theLightComesOnBeforeTheCue() {
        val p = Pieces.ALL[0]
        val cue = p.cues.first() as Cue.Enter
        val perf = Performance(p)
        perf.advance(p.time(cue.beat) - Performance.LEAD_S - 1, 0.0)
        assertNull(perf.wants(cue.section))
        perf.advance(p.time(cue.beat) - 1, 0.0)
        assertEquals(cue, perf.wants(cue.section))
    }

    @Test
    fun theFirstEntryIsNeverAtTheVeryStartOfThePlayersTime() {
        for (p in Pieces.ALL) assertTrue(p.id, Performance.countIn(p) >= 2.0)
    }

    @Test
    fun trophiesAndSavedProgress() {
        val pr = Progress()
        assertTrue(pr.open(Pieces.ALL[0]))
        assertFalse(pr.open(Pieces.ALL[1]))
        for (p in Pieces.ALL) pr.record(p.id, 1)
        assertEquals(1, pr.trophies)
        for (p in Pieces.ALL.take(4)) pr.record(p.id, 3)
        assertEquals(2, pr.trophies)
        for (p in Pieces.ALL) pr.record(p.id, 3)
        assertEquals(3, pr.trophies)
        assertEquals(pr.best, Progress.load(pr.save()).best)
    }

    @Test
    fun theWordsAvoidTheForbiddenOnes() {
        val bad = listOf("老虎机", "投币", "赌", "筹码", "奖金", "大奖", "中奖", "押注", "彩金", "赔率", "硬币", "翻倍", "彩票", "下注", "抽奖", "博彩", "轮盘")
        for (p in Pieces.ALL) for (b in bad) assertFalse(p.lore, b in p.lore + p.title + p.composer)
    }
}
