package com.bungbungi.app.logic

import kotlin.math.abs

/**
 * 주행 신호를 시간 순서대로 받아 위반·보너스 이벤트를 만든다. 순수 Kotlin이라 단위 테스트로 검증한다.
 * 기준값은 기획서 초기값이며, 시연·테스트를 보며 [Config]로 조정한다.
 */
class ViolationDetector(private val config: Config = Config()) {

    data class Config(
        val speedingMarginKmh: Float = 10f,
        val speedingHoldMillis: Long = 3_000,
        val zoneHoldMillis: Long = 2_000,
        val harshDeltaKmh: Float = 10f,
        val harshWindowMillis: Long = 1_000,
        val harshCooldownMillis: Long = 3_000,
        val laneChangeMinSpeedKmh: Float = 40f,
        val laneChangeSteeringDeg: Float = 20f,
        val signalLookbackMillis: Long = 3_000,
        val laneChangeCooldownMillis: Long = 5_000,
        val seatbeltMinSpeedKmh: Float = 5f,
        val cleanBonusKm: Double = 30.0,
    )

    private data class SpeedPoint(val t: Long, val v: Float)

    private val speedHistory = ArrayDeque<SpeedPoint>()
    private var overLimitSince: Long? = null
    private var speedingReported = false
    private var zoneOverSince: Long? = null
    private var zoneReported = false
    private var lastAccelEvent = Long.MIN_VALUE / 2
    private var lastBrakeEvent = Long.MIN_VALUE / 2
    private var lastLaneEvent = Long.MIN_VALUE / 2
    private var lastSignalOnAt = Long.MIN_VALUE / 2
    private var seatbeltReported = false
    private var cruiseBonusGiven = false
    private var cleanKm = 0.0
    private var lastTime: Long? = null

    /** 한 샘플을 처리하고, 이 샘플에서 새로 생긴 이벤트를 돌려준다. distanceKm는 직전 샘플 이후 달린 거리. */
    fun process(s: DriveSample, distanceKm: Double = 0.0): Result {
        val violations = mutableListOf<ViolationEvent>()
        val bonuses = mutableListOf<BonusEvent>()
        val t = s.timeMillis
        val v = s.speedKmh

        if (s.turnSignalOn) lastSignalOnAt = t

        if (v != null) {
            speedHistory.addLast(SpeedPoint(t, v))
            while (speedHistory.size > 2 && t - speedHistory.first().t > config.harshWindowMillis * 3) {
                speedHistory.removeFirst()
            }

            // 보호구역 과속: 주의구간 안에서 제한속도 초과
            val limit = s.speedLimitKmh?.takeIf { it > 0 }
            if (s.inCautionZone && limit != null && v > limit) {
                val since = zoneOverSince ?: t.also { zoneOverSince = it }
                if (!zoneReported && t - since >= config.zoneHoldMillis) {
                    zoneReported = true
                    violations += ViolationEvent(Violation.ZONE_SPEEDING, t, "보호구역 ${limit}km/h 구간에서 ${v.toInt()}km/h")
                }
            } else {
                zoneOverSince = null
                zoneReported = false
            }

            // 과속: 제한속도 +10km/h를 3초 이상 (보호구역 과속과 중복 감점하지 않음)
            if (!s.inCautionZone && limit != null && v > limit + config.speedingMarginKmh) {
                val since = overLimitSince ?: t.also { overLimitSince = it }
                if (!speedingReported && t - since >= config.speedingHoldMillis) {
                    speedingReported = true
                    violations += ViolationEvent(Violation.SPEEDING, t, "제한 ${limit}km/h 도로에서 ${v.toInt()}km/h")
                }
            } else {
                overLimitSince = null
                speedingReported = false
            }

            // 급가속·급감속: 약 1초 전 속도와 비교
            val ref = speedHistory.lastOrNull { t - it.t >= config.harshWindowMillis }
            if (ref != null && t - ref.t <= config.harshWindowMillis * 2) {
                val perSecond = (v - ref.v) * 1000f / (t - ref.t)
                if (perSecond >= config.harshDeltaKmh && t - lastAccelEvent >= config.harshCooldownMillis) {
                    lastAccelEvent = t
                    violations += ViolationEvent(Violation.HARSH_ACCEL, t, "1초에 ${perSecond.toInt()}km/h 가속")
                }
                val brakeOk = s.brakePressed != false // 브레이크 신호가 없으면 속도만으로 판단
                if (-perSecond >= config.harshDeltaKmh && brakeOk && t - lastBrakeEvent >= config.harshCooldownMillis) {
                    lastBrakeEvent = t
                    violations += ViolationEvent(Violation.HARSH_BRAKE, t, "1초에 ${(-perSecond).toInt()}km/h 감속")
                }
            }

            // 깜빡이 없는 차선변경
            val angle = s.steeringAngleDeg
            if (angle != null && v >= config.laneChangeMinSpeedKmh && abs(angle) >= config.laneChangeSteeringDeg &&
                !s.turnSignalOn && t - lastSignalOnAt > config.signalLookbackMillis &&
                t - lastLaneEvent >= config.laneChangeCooldownMillis
            ) {
                lastLaneEvent = t
                violations += ViolationEvent(Violation.NO_SIGNAL_LANE_CHANGE, t, "핸들 ${angle.toInt()}°, 방향지시등 꺼짐")
            }

            // 안전벨트: 주행 중 미착용 경고가 켜질 때마다 1회
            if (s.seatbeltWarning && v >= config.seatbeltMinSpeedKmh) {
                if (!seatbeltReported) {
                    seatbeltReported = true
                    violations += ViolationEvent(Violation.SEATBELT, t, "주행 중 안전벨트 미착용")
                }
            } else if (!s.seatbeltWarning) {
                seatbeltReported = false
            }
        }

        // 보너스
        if (s.cruiseOn && !cruiseBonusGiven) {
            cruiseBonusGiven = true
            bonuses += BonusEvent(BonusType.CRUISE, t)
        }
        cleanKm = if (violations.isEmpty()) cleanKm + distanceKm else 0.0
        if (cleanKm >= config.cleanBonusKm) {
            cleanKm -= config.cleanBonusKm
            bonuses += BonusEvent(BonusType.CLEAN_30KM, t)
        }
        lastTime = t
        return Result(violations, bonuses)
    }

    data class Result(val violations: List<ViolationEvent>, val bonuses: List<BonusEvent>)
}
