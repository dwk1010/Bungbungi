package com.bungbungi.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViolationDetectorTest {

    private fun run(samples: List<DriveSample>): List<Violation> {
        val d = ViolationDetector()
        return samples.flatMap { d.process(it).violations.map { v -> v.type } }
    }

    private fun steady(from: Long, to: Long, step: Long = 500, f: (Long) -> DriveSample) =
        (from..to step step).map(f)

    @Test fun speedingNeedsThreeSeconds() {
        val short = steady(0, 2_000) { DriveSample(it, 85f, speedLimitKmh = 70) }
        assertEquals(emptyList<Violation>(), run(short))
        val long = steady(0, 4_000) { DriveSample(it, 85f, speedLimitKmh = 70) }
        assertEquals(listOf(Violation.SPEEDING), run(long))
    }

    @Test fun withinMarginIsNotSpeeding() {
        assertEquals(emptyList<Violation>(), run(steady(0, 10_000) { DriveSample(it, 79f, speedLimitKmh = 70) }))
    }

    @Test fun zoneSpeedingReplacesSpeeding() {
        val s = steady(0, 5_000) { DriveSample(it, 45f, speedLimitKmh = 30, inCautionZone = true) }
        assertEquals(listOf(Violation.ZONE_SPEEDING), run(s))
    }

    @Test fun harshAcceleration() {
        val s = listOf(DriveSample(0, 30f), DriveSample(500, 37f), DriveSample(1_000, 45f))
        assertEquals(listOf(Violation.HARSH_ACCEL), run(s))
    }

    @Test fun gentleAccelerationIsFine() {
        val s = (0..10).map { DriveSample(it * 1_000L, 30f + it * 5f) }
        assertEquals(emptyList<Violation>(), run(s))
    }

    @Test fun harshBrakeNeedsPedal() {
        val noPedal = listOf(DriveSample(0, 60f, brakePressed = false), DriveSample(1_000, 45f, brakePressed = false))
        assertEquals(emptyList<Violation>(), run(noPedal))
        val pedal = listOf(DriveSample(0, 60f, brakePressed = true), DriveSample(1_000, 45f, brakePressed = true))
        assertEquals(listOf(Violation.HARSH_BRAKE), run(pedal))
    }

    @Test fun laneChangeWithoutSignal() {
        val s = listOf(DriveSample(10_000, 60f, steeringAngleDeg = 0f), DriveSample(10_500, 60f, steeringAngleDeg = 25f))
        assertEquals(listOf(Violation.NO_SIGNAL_LANE_CHANGE), run(s))
    }

    @Test fun laneChangeWithRecentSignalIsFine() {
        val s = listOf(
            DriveSample(10_000, 60f, turnSignalOn = true, steeringAngleDeg = 0f),
            DriveSample(11_000, 60f, turnSignalOn = false, steeringAngleDeg = 25f),
        )
        assertEquals(emptyList<Violation>(), run(s))
    }

    @Test fun seatbeltOncePerWarning() {
        val s = steady(0, 5_000) { DriveSample(it, 30f, seatbeltWarning = true) }
        assertEquals(listOf(Violation.SEATBELT), run(s))
    }

    @Test fun cleanDistanceGivesBonus() {
        val d = ViolationDetector()
        val bonuses = (1..40).flatMap { d.process(DriveSample(it * 1_000L, 60f), distanceKm = 1.0).bonuses }
        assertTrue(bonuses.any { it.type == BonusType.CLEAN_30KM })
    }
}
