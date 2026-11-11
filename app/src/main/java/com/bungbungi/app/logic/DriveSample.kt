package com.bungbungi.app.logic

/**
 * 감지기에 들어가는 한 시점의 주행 신호.
 * 값이 null이면 그 신호를 아직 못 받은 것이라 해당 규칙은 건너뛴다.
 */
data class DriveSample(
    val timeMillis: Long,
    val speedKmh: Float?,
    val speedLimitKmh: Int? = null,
    val inCautionZone: Boolean = false,
    val brakePressed: Boolean? = null,
    val turnSignalOn: Boolean = false,
    val steeringAngleDeg: Float? = null,
    val seatbeltWarning: Boolean = false,
    val cruiseOn: Boolean = false,
)
