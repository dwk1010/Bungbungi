package com.bungbungi.app.logic

import kotlin.math.max
import kotlin.math.min

/** 주행 한 번의 기록 */
data class TripRecord(
    val id: Long,
    val startMillis: Long,
    val endMillis: Long,
    val distanceKm: Double,
    val counts: Map<Violation, Int>,
    val bonusCount: Int,
) {
    val score: Int get() = Scoring.tripScore(distanceKm, counts, bonusCount)
    val violationTotal: Int get() = counts.values.sum()
}

object Scoring {
    const val BONUS_POINTS = 2

    /** S_trip = clamp(100 - Σ w_i · n_i / d · 100 + B, 0, 100). 거리가 0에 가까우면 1km로 본다. */
    fun tripScore(distanceKm: Double, counts: Map<Violation, Int>, bonusCount: Int): Int {
        val d = max(distanceKm, 1.0)
        val penalty = counts.entries.sumOf { (v, n) -> v.penalty * n / d * 100.0 }
        val raw = 100.0 - penalty + bonusCount * BONUS_POINTS
        return min(100.0, max(0.0, raw)).toInt()
    }

    /** 거리 가중 평균 점수. 거리가 없으면 null */
    fun weightedScore(trips: List<TripRecord>): Double? {
        val total = trips.sumOf { it.distanceKm }
        if (total <= 0.0) return null
        return trips.sumOf { it.score * it.distanceKm } / total
    }

    /** 가장 감점이 큰 위반 (감점 × 횟수 기준) */
    fun dominantViolation(trips: List<TripRecord>): Violation? {
        val sum = mutableMapOf<Violation, Int>()
        trips.forEach { t -> t.counts.forEach { (v, n) -> sum[v] = (sum[v] ?: 0) + n * v.penalty } }
        return sum.filter { it.value > 0 }.maxByOrNull { it.value }?.key
    }
}
