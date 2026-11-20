package com.vrgames.conductor.core

import kotlin.math.abs

/** One group of instruments, and the stem it plays. */
data class Section(val id: String, val name: String)

/** What the score asks of a section at a moment of the music. Beats count from the start of the stems. */
sealed interface Cue {
    val section: String
    val beat: Double

    /** Bring the section in. */
    data class Enter(override val section: String, override val beat: Double) : Cue

    /** Lead its volume from [from] to [to] over these beats, with the hand held up or down. */
    data class Swell(override val section: String, override val beat: Double, val endBeat: Double, val from: Double, val to: Double) : Cue

    /** Let it stop: let go of the pinch here. */
    data class Release(override val section: String, override val beat: Double) : Cue
}

class Piece(
    val id: String,
    val title: String,
    val composer: String,
    val chapter: Int,
    val lore: String,
    val bpm: Double,
    val perBar: Int,
    val bars: Int,
    val seconds: Double,
    val sections: List<Section>,
    val cues: List<Cue>,
) {
    val secondsPerBeat get() = 60.0 / bpm
    fun time(beat: Double) = beat * secondsPerBeat
    fun beat(time: Double) = time / secondsPerBeat
}

enum class Grade(val points: Double, val word: String) {
    PERFECT(1.0, "正好"),
    GOOD(0.65, "不错"),
    MISS(0.0, "错过了"),
}

/** What happened, for the view, the sound and the words on the panel. */
sealed interface Ev {
    data class Entered(val section: String, val grade: Grade?) : Ev
    data class Released(val section: String, val grade: Grade?) : Ev
    data class Missed(val section: String, val cue: Cue) : Ev
    /** A pinch before the light turned bright: nothing is spent, the player can pinch again on time. */
    data class Early(val section: String) : Ev
    data class SwellDone(val section: String, val score: Double) : Ev
    data object Finished : Ev
    /** A beat of the conducting hand, [onTime] when it fell with the music's beat. */
    data class Beat(val onTime: Boolean) : Ev
}

/**
 * The conductor's run of one piece. Pure: the mixer asks [gain] for each stem's volume, the view asks [wants] what to
 * light, and the clock is whatever time it is given. The music never stops: a missed entry only leaves that section silent
 * until the player brings it in late, which scores nothing but sounds right again.
 */
class Performance(val piece: Piece) {
    private class State(val section: Section) {
        var on = false
        var level = DEFAULT_LEVEL
        var holding = false
        var shown = 0.0
        /** How long it has been held (seconds), for the solo. */
        var heldFor = 0.0
    }

    /**
     * v0.3 (PICO 1117: 「里边的乐器模型只是个展示，无法交互」): hold a playing section for [SOLO_AFTER] seconds and it takes
     * a solo — it plays out louder and the others drop back — until the fingers part. The section soloing now, or null.
     */
    fun solo(): String? = states.values.firstOrNull { it.holding && it.on && it.heldFor >= SOLO_AFTER }?.section?.id

    /** The gain factor for [section] while someone solos: up for the soloist, well back for the rest. */
    fun soloFactor(section: String): Double {
        val s = solo() ?: return 1.0
        return if (s == section) SOLO_UP else SOLO_DOWN
    }

    private class Judged(val cue: Cue) {
        var done = false
        var points = 0.0
        // For a swell: how closely the volume followed, summed over time.
        var followed = 0.0
        var followTime = 0.0
    }

    private val states = piece.sections.associate { it.id to State(it) }
    private val judged = piece.cues.map { Judged(it) }
    private val events = ArrayList<Ev>()
    var time = -countIn(piece); private set
    var finished = false; private set

    val beat get() = piece.beat(time)

    fun drain(): List<Ev> = events.toList().also { events.clear() }

    fun isOn(section: String) = states.getValue(section).on
    fun isHolding(section: String) = states.getValue(section).holding
    fun level(section: String) = states.getValue(section).level

    /** The stem's volume now, smoothed so nothing clicks. */
    fun gain(section: String) = states.getValue(section).shown

    /** What this section is being asked for right now, for its light and label; the next thing within the lead time. */
    fun wants(section: String): Cue? = judged.firstOrNull { j ->
        j.cue.section == section && !j.done && when (val c = j.cue) {
            is Cue.Swell -> time >= piece.time(c.beat) - LEAD_S && time <= piece.time(c.endBeat)
            else -> time >= piece.time(c.beat) - LEAD_S && time <= piece.time(c.beat) + GOOD_LATE_S
        }
    }?.cue

    /** The swell's wanted volume at this moment, for the marker beside the section. */
    fun swellTarget(section: String): Double? = judged.firstOrNull { j ->
        val c = j.cue
        c is Cue.Swell && c.section == section && time >= piece.time(c.beat) && time <= piece.time(c.endBeat)
    }?.let { target(it.cue as Cue.Swell) }

    private fun target(c: Cue.Swell): Double {
        val u = ((beat - c.beat) / (c.endBeat - c.beat)).coerceIn(0.0, 1.0)
        return c.from + (c.to - c.from) * u
    }

    /**
     * How a pinch now fits [cue]. The window leans late: a player reacts to the light turning bright, and hand tracking
     * adds its own delay, so a pinch a little after the beat is as good as one a little before.
     */
    private fun grade(cue: Cue): Grade {
        val d = time - piece.time(cue.beat)
        return when {
            d >= -PERFECT_EARLY_S && d <= PERFECT_LATE_S -> Grade.PERFECT
            d >= -GOOD_EARLY_S && d <= GOOD_LATE_S -> Grade.GOOD
            else -> Grade.MISS
        }
    }

    /** The bright light: the moment to pinch (or let go) has come, inside the window that still counts. */
    fun due(section: String): Cue? = judged.firstOrNull { j ->
        j.cue.section == section && !j.done && when (val c = j.cue) {
            is Cue.Swell -> time >= piece.time(c.beat) - DUE_S && time <= piece.time(c.endBeat)
            else -> time >= piece.time(c.beat) - DUE_S && time <= piece.time(c.beat) + GOOD_LATE_S
        }
    }?.cue

    /** A pinch on a section: brings it in if it is silent, and starts holding it either way. */
    fun pinch(section: String) {
        if (finished) return
        val s = states[section] ?: return
        s.holding = true
        if (s.on) {
            // Already playing (say a release was missed), but an entry is due: it counts as given.
            judged.firstOrNull { it.cue is Cue.Enter && it.cue.section == section && !it.done && grade(it.cue) != Grade.MISS }?.let { j ->
                val g = grade(j.cue)
                j.done = true
                j.points = g.points
                events += Ev.Entered(section, g)
            }
            return
        }
        val j = judged.firstOrNull { it.cue is Cue.Enter && it.cue.section == section && !it.done && grade(it.cue) != Grade.MISS }
        if (j == null && judged.any { it.cue is Cue.Enter && it.cue.section == section && !it.done && time < piece.time(it.cue.beat) - GOOD_EARLY_S && time >= piece.time(it.cue.beat) - LEAD_S }) {
            // Its entry is coming but not yet: say so, and leave the section silent so the real pinch still counts.
            s.holding = false
            events += Ev.Early(section)
            return
        }
        s.on = true
        s.level = DEFAULT_LEVEL
        if (j != null) {
            val g = grade(j.cue)
            j.done = true
            j.points = g.points
            events += Ev.Entered(section, g)
        } else {
            events += Ev.Entered(section, null)
        }
    }

    /** The held hand's height, 0 (down) to 1 (up), sets the section's volume. */
    fun hold(section: String, level: Double) {
        val s = states[section] ?: return
        if (s.holding && s.on) s.level = level.coerceIn(0.0, 1.0)
    }

    /** The fingers part. If a release is due, the section stops; otherwise it keeps playing at its volume. */
    fun letGo(section: String) {
        val s = states[section] ?: return
        s.holding = false
        if (finished) return
        val j = judged.firstOrNull { it.cue is Cue.Release && it.cue.section == section && !it.done && grade(it.cue) != Grade.MISS } ?: return
        val g = grade(j.cue)
        j.done = true
        j.points = g.points
        s.on = false
        events += Ev.Released(section, g)
    }

    fun advance(now: Double, dt: Double) {
        for (s in states.values) s.heldFor = if (s.holding) s.heldFor + dt else 0.0
        if (finished) return
        time = now
        for (j in judged) {
            if (j.done) continue
            when (val c = j.cue) {
                is Cue.Swell -> {
                    val a = piece.time(c.beat)
                    val b = piece.time(c.endBeat)
                    if (time in a..b) {
                        val s = states.getValue(c.section)
                        // Only a held hand conducts the swell; a section that is not playing follows nothing.
                        val close = if (s.on && s.holding) (1.0 - abs(s.level - target(c)) / SWELL_TOLERANCE).coerceIn(0.0, 1.0) else 0.0
                        j.followed += close * dt
                        j.followTime += dt
                    } else if (time > b) {
                        j.done = true
                        j.points = if (j.followTime > 0) j.followed / j.followTime else 0.0
                        events += Ev.SwellDone(c.section, j.points)
                    }
                }
                else -> if (time > piece.time(c.beat) + GOOD_LATE_S) {
                    j.done = true
                    events += Ev.Missed(c.section, c)
                }
            }
        }
        conducting = maxOf(0.0, conducting - dt / CONDUCT_FADE_S)
        for (s in states.values) {
            // The tune is always there, quietly, so the piece can be recognized before its section is
            // brought in (2026-11-12 review: what played did not sound like the piece shown).
            val want = if (s.on) s.level else if (s.section.id == lead) GUIDE_LEVEL else 0.0
            val step = dt * GAIN_RATE
            s.shown = if (s.shown < want) minOf(want, s.shown + step) else maxOf(want, s.shown - step)
        }
        if (time >= piece.seconds) {
            finished = true
            events += Ev.Finished
        }
    }

    /** The section that carries the tune (the first one listed). */
    private val lead = piece.sections.first().id

    /** How much the conducting hand is keeping the beat, 0 (still) to 1; it fades without beats. */
    var conducting = 0.0; private set
    var beatsOnTime = 0; private set
    var beatsGiven = 0; private set

    /**
     * The conducting hand gave a beat (the bottom of a stroke) now: on time when it falls within [BEAT_WINDOW_S] of one of
     * the music's beats. On-time beats bring the orchestra to full; it plays a little softer when nobody conducts.
     */
    fun conductBeat() {
        if (finished || time < 0) return
        val b = beat
        val off = abs(b - kotlin.math.round(b)) * piece.secondsPerBeat
        val onTime = off <= BEAT_WINDOW_S
        beatsGiven++
        if (onTime) beatsOnTime++
        conducting = minOf(1.0, conducting + if (onTime) 0.35 else 0.12)
        events += Ev.Beat(onTime)
    }

    /** The whole orchestra's volume: full while conducted, softer without a hand. */
    fun master() = 0.72 + 0.28 * conducting

    /** The next section still to bring in, and how many beats until its cue (negative: it is late). */
    fun nextEntry(): Pair<String, Double>? = judged.firstOrNull { !it.done && it.cue is Cue.Enter }?.let { it.cue.section to (it.cue.beat - beat) }

    /** 0 to 1 over all the cues. */
    fun accuracy(): Double = if (judged.isEmpty()) 1.0 else judged.sumOf { it.points } / judged.size

    fun stars(): Int {
        val a = accuracy()
        return when {
            a >= 0.85 -> 3
            a >= 0.6 -> 2
            a >= 0.3 -> 1
            else -> 0
        }
    }

    companion object {
        // The window leans late (reaction to the light plus hand-tracking delay). The device test of 2026-11-08 judged
        // every pinch late with a 0.15 s / 0.45 s window and a light that came on 2.5 s early with no "now" in it.
        const val PERFECT_EARLY_S = 0.45
        const val PERFECT_LATE_S = 0.35
        const val GOOD_EARLY_S = 0.8
        const val GOOD_LATE_S = 0.7
        /** The light turns from "get ready" to bright "now" this long before the beat. */
        const val DUE_S = 0.6
        const val LEAD_S = 2.5
        const val DEFAULT_LEVEL = 0.8
        const val SWELL_TOLERANCE = 0.35
        const val GAIN_RATE = 3.0
        /** The tune's level before its section is brought in. */
        const val GUIDE_LEVEL = 0.22
        const val BEAT_WINDOW_S = 0.16
        const val CONDUCT_FADE_S = 3.0

        /** Two bars of silence before the music, so the first entry can be seen coming. */
        /** Held this long, a playing section takes a solo. */
        const val SOLO_AFTER = 0.6
        const val SOLO_UP = 1.3
        const val SOLO_DOWN = 0.3
        fun countIn(p: Piece) = 2 * p.perBar * p.secondsPerBeat
    }
}

/** Stars per piece, saved as "id:stars". */
class Progress(val best: MutableMap<String, Int> = LinkedHashMap()) {
    val stars get() = best.values.sum()
    val maxStars get() = Pieces.ALL.size * 3
    fun open(p: Piece): Boolean {
        val k = Pieces.ALL.indexOf(p)
        return k == 0 || (best[Pieces.ALL[k - 1].id] ?: 0) > 0 || (best[p.id] ?: 0) > 0
    }
    val bronze get() = Pieces.ALL.all { (best[it.id] ?: 0) > 0 }
    val silver get() = Pieces.ALL.count { (best[it.id] ?: 0) == 3 } >= Pieces.ALL.size / 2
    val gold get() = Pieces.ALL.all { (best[it.id] ?: 0) == 3 }
    val trophies get() = if (gold) 3 else if (silver && bronze) 2 else if (bronze) 1 else 0
    fun record(id: String, s: Int) { if (s > (best[id] ?: 0)) best[id] = s }
    fun save() = best.entries.joinToString(",") { "${it.key}:${it.value}" }

    companion object {
        fun load(text: String) = Progress().also { p ->
            text.split(",").filter { it.contains(":") }.forEach {
                val (k, v) = it.split(":")
                p.best[k] = v.toIntOrNull() ?: 0
            }
        }
    }
}

/** Plays a piece perfectly, for the tests and the emulator walk. */
class Bot(private val perf: Performance) {
    private val todo = perf.piece.cues.sortedBy { it.beat }.toMutableList()
    private val heldUntil = HashMap<String, Double>()

    fun act() {
        val p = perf.piece
        val t = perf.time
        val it = todo.iterator()
        while (it.hasNext()) {
            when (val c = it.next()) {
                is Cue.Enter -> if (t >= p.time(c.beat) - 0.02) {
                    perf.pinch(c.section)
                    perf.letGo(c.section)
                    it.remove()
                }
                is Cue.Release -> if (t >= p.time(c.beat) - 0.02) {
                    perf.pinch(c.section)
                    perf.letGo(c.section)
                    it.remove()
                }
                is Cue.Swell -> if (t >= p.time(c.beat) - 0.02) {
                    if (!perf.isOn(c.section)) perf.pinch(c.section) else if (!perf.isHolding(c.section)) perf.pinch(c.section)
                    heldUntil[c.section] = p.time(c.endBeat)
                    it.remove()
                }
            }
        }
        for ((section, until) in heldUntil.toList()) {
            val want = perf.swellTarget(section)
            if (want != null) perf.hold(section, want)
            if (t > until) {
                perf.letGo(section)
                heldUntil.remove(section)
            }
        }
    }
}
