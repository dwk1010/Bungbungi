package com.bungbungi.app.logic

import com.bungbungi.app.logic.AvatarEngine.DAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarEngineTest {

    private fun trip(day: Long, km: Double, vararg v: Pair<Violation, Int>) =
        TripRecord(day, day * DAY, day * DAY + 1_000, km, v.toMap(), 0)

    @Test fun scoreFormula() {
        assertEquals(100, Scoring.tripScore(10.0, emptyMap(), 0))
        assertEquals(70, Scoring.tripScore(10.0, mapOf(Violation.SPEEDING to 1), 0))
        assertEquals(0, Scoring.tripScore(1.0, mapOf(Violation.ZONE_SPEEDING to 1), 0))
    }

    @Test fun eggUntilTwoWeeks() {
        val s = AvatarEngine.evaluate(AvatarState(0), listOf(trip(1, 20.0)), 13 * DAY)
        assertFalse(s.born)
    }

    @Test fun hatchesFromTwoWeekScore() {
        val trips = (0 until 14L).map { trip(it, 20.0) }
        val s = AvatarEngine.evaluate(AvatarState(0), trips, 14 * DAY)
        assertTrue(s.born)
        assertEquals(Stage.ANGEL, s.stage)
    }

    @Test fun movesOneStepPerWeek() {
        val good = (0 until 14L).map { trip(it, 20.0) }
        var s = AvatarEngine.evaluate(AvatarState(0), good, 14 * DAY)
        // 3주차: 매일 과속 3회 → 점수 급락, 그래도 한 칸만 내려감
        val bad = (14 until 21L).map { trip(it, 10.0, Violation.SPEEDING to 3) }
        s = AvatarEngine.evaluate(s, good + bad, 21 * DAY)
        assertEquals(Stage.GENTLE, s.stage)
        assertEquals(Stage.ANGEL, s.lastChange?.from)
    }

    @Test fun restingWeekKeepsStage() {
        val good = (0 until 14L).map { trip(it, 20.0) }
        var s = AvatarEngine.evaluate(AvatarState(0), good, 14 * DAY)
        s = AvatarEngine.evaluate(s, good + trip(15, 2.0, Violation.SPEEDING to 5), 21 * DAY)
        assertEquals(Stage.ANGEL, s.stage)
        assertTrue(s.resting)
    }

    @Test fun dominantViolationSetsPart() {
        val bad = (0 until 14L).map { trip(it, 10.0, Violation.HARSH_ACCEL to 4) }
        val s = AvatarEngine.evaluate(AvatarState(0), bad, 14 * DAY)
        assertEquals(Stage.WILD, s.stage)
        assertEquals(AvatarPart.EXHAUST_SMOKE, s.part)
    }
}
