package com.bungbungi.app.demo

/**
 * 2차 PT 시연용 주행 시나리오. 실제 차량 신호 대신 이 값을 감지기에 넣는다.
 * 실제 시간으로 약 40~50초지만, [nominalKm]만큼 달린 것으로 계산해서 100km당 점수가 현실적으로 나오게 한다.
 */
data class ScenarioFrame(
    val speedKmh: Float,
    val limitKmh: Int?,
    val schoolZone: Boolean = false,
    val brake: Boolean = false,
    val turn: String = "OFF",
    val steering: Float = 0f,
    val seatbeltWarning: Boolean = false,
    val cruise: Boolean = false,
    val caption: String = "",
)

class Scenario(val title: String, val nominalKm: Double, val frames: List<ScenarioFrame>) {
    /** 프레임 간격 */
    val stepMillis = 250L
    val durationMillis get() = frames.size * stepMillis
}

object Scenarios {

    private class Builder {
        val frames = mutableListOf<ScenarioFrame>()
        var v = 0f
        var limit: Int? = 60
        var zone = false
        var caption = ""

        /** seconds 동안 속도를 target까지 선형으로 바꾼다 */
        fun ramp(target: Float, seconds: Double, brake: Boolean = false, turn: String = "OFF", steering: Float = 0f, belt: Boolean = false, cruise: Boolean = false) {
            val n = (seconds * 4).toInt().coerceAtLeast(1)
            val start = v
            repeat(n) { i ->
                v = start + (target - start) * (i + 1) / n
                frames += ScenarioFrame(v, limit, zone, brake, turn, steering, belt, cruise, caption)
            }
        }

        fun hold(seconds: Double, turn: String = "OFF", steering: Float = 0f, belt: Boolean = false, cruise: Boolean = false, brake: Boolean = false) =
            ramp(v, seconds, brake, turn, steering, belt, cruise)
    }

    val safe: Scenario by lazy {
        val b = Builder()
        b.caption = "부드럽게 출발"; b.limit = 60; b.ramp(50f, 8.0)
        b.caption = "제한속도 60km/h 정속 주행"; b.hold(5.0)
        b.caption = "깜빡이 켜고 차선변경"; b.hold(1.5, turn = "LEFT"); b.hold(1.0, turn = "LEFT", steering = 22f); b.hold(1.0)
        b.caption = "크루즈 컨트롤 사용"; b.ramp(60f, 4.0); b.hold(5.0, cruise = true)
        b.caption = "어린이보호구역 30km/h"; b.ramp(28f, 6.0, brake = true); b.zone = true; b.limit = 30; b.hold(5.0); b.zone = false; b.limit = 60
        b.caption = "천천히 정차"; b.ramp(0f, 7.0, brake = true); b.hold(1.0, brake = true)
        Scenario("안전 주행", nominalKm = 20.0, frames = b.frames)
    }

    val rough: Scenario by lazy {
        val b = Builder()
        b.caption = "벨트 안 매고 출발"; b.limit = 60; b.ramp(20f, 2.0, belt = true)
        b.caption = "급가속"; b.ramp(55f, 2.5)
        b.caption = "제한 70km/h 도로에서 95km/h"; b.limit = 70; b.ramp(95f, 3.0); b.hold(4.5)
        b.caption = "깜빡이 없이 차선변경"; b.hold(0.75, steering = 28f); b.hold(1.0)
        b.caption = "급정거"; b.ramp(60f, 1.5, brake = true); b.hold(1.0)
        b.caption = "어린이보호구역 과속"; b.zone = true; b.limit = 30; b.ramp(45f, 2.0, brake = true); b.hold(3.5); b.zone = false; b.limit = 60
        b.caption = "또 급가속"; b.ramp(80f, 2.5)
        b.caption = "깜빡이 없이 끼어들기"; b.hold(0.75, steering = -30f); b.hold(1.5)
        b.caption = "급정거 후 정차"; b.ramp(40f, 1.25, brake = true); b.ramp(0f, 3.0, brake = true); b.hold(1.0, brake = true)
        Scenario("난폭 주행", nominalKm = 10.0, frames = b.frames)
    }
}
