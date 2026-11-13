package com.vrgames.conductor.core

/**
 * Finds a conductor's beats in one hand's height over time (2026-11-12 review: conduct by the hand's own motion, not only
 * pinch and drag). A beat is the bottom of a downstroke: the hand came down at least [DROP_M] from its last high point
 * and has started back up by [REBOUND_M]. Beats closer than [MIN_GAP_S] are one beat.
 */
class BeatHand {
    private var high = Double.NaN
    private var low = Double.NaN
    private var lowAt = 0.0
    private var lastBeat = -1e9

    /** A new height [y] (meters) at time [t] (seconds); true when a beat just ended (at the bottom of the stroke). */
    fun feed(t: Double, y: Double): Boolean {
        if (high.isNaN()) { high = y; low = y; lowAt = t; return false }
        if (y > high && low >= high - DROP_M) { high = y; low = y; lowAt = t }
        if (y < low) { low = y; lowAt = t }
        if (high - low >= DROP_M && y - low >= REBOUND_M && lowAt - lastBeat >= MIN_GAP_S) {
            lastBeat = lowAt
            high = y; low = y; lowAt = t
            return true
        }
        return false
    }

    fun reset() { high = Double.NaN }

    companion object {
        const val DROP_M = 0.05
        const val REBOUND_M = 0.015
        const val MIN_GAP_S = 0.25
    }
}
