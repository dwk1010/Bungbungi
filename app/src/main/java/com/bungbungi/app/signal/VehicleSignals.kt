package com.bungbungi.app.signal

/** 속도를 얻는 후보 경로. 에뮬레이터에서 어느 것이 실제로 읽히는지 신호 화면에서 확인한다. */
enum class SpeedSource(val label: String, val api: String, val sticky: Boolean) {
    PLEOS_PROPERTY("Pleos 차량 서비스 속성", "IVehicleService PERF_VEHICLE_SPEED", true),
    CAR_PROPERTY("AAOS 차량 속성", "CarPropertyManager PERF_VEHICLE_SPEED", true),
    GPS("GPS 위치 속도", "LocationManager Location.speed", false),
    FUSED_LOCATION("FusedLocation 위치 변화", "FusedLocation 좌표 차이 ÷ 시간", false),
    ODOMETER("주행거리계 변화", "Odometer DistanceDriven 차이 ÷ 시간", false),
}

data class SpeedReading(
    val kmh: Float? = null,
    val atMillis: Long = 0,
    val status: String = "대기",
    val raw: String? = null,
    val updates: Int = 0,
)

/** 차량·내비·위치에서 모은 현재 신호. 값이 null이면 아직 못 받음. */
data class VehicleSignals(
    val speeds: Map<SpeedSource, SpeedReading> = SpeedSource.entries.associateWith { SpeedReading() },
    val brakePressed: Boolean? = null,
    val turnSignal: String? = null,
    val steeringAngleDeg: Float? = null,
    val odometerKm: Float? = null,
    val seatbeltWarning: Boolean? = null,
    val cruise: String? = null,
    val gear: String? = null,
    val speedLimitKmh: Int? = null,
    val cautionZone: String? = null,
    val cameraOverSpeed: Boolean = false,
    val vehicleStatus: String = "연결 전",
    val naviStatus: String = "연결 전",
    val locationStatus: String = "연결 전",
) {
    val turnSignalOn: Boolean get() = turnSignal == "LEFT" || turnSignal == "RIGHT"
    val cruiseOn: Boolean get() = cruise == "ACTIVATED"

    /** 우선순위대로 살아 있는 속도 하나를 고른다. 속성 기반 값은 바뀔 때만 오므로 오래돼도 유효하다. */
    fun bestSpeed(now: Long): Pair<SpeedSource, Float>? {
        for (src in SpeedSource.entries) {
            val r = speeds[src] ?: continue
            val v = r.kmh ?: continue
            if (src.sticky || now - r.atMillis <= 5_000) return src to v
        }
        return null
    }
}
