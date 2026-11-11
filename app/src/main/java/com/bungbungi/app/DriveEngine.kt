package com.bungbungi.app

import android.content.Context
import com.bungbungi.app.data.Store
import com.bungbungi.app.demo.Scenario
import com.bungbungi.app.logic.AvatarEngine
import com.bungbungi.app.logic.AvatarState
import com.bungbungi.app.logic.BonusEvent
import com.bungbungi.app.logic.DriveSample
import com.bungbungi.app.logic.Stage
import com.bungbungi.app.logic.StageChange
import com.bungbungi.app.logic.TripRecord
import com.bungbungi.app.logic.Violation
import com.bungbungi.app.logic.ViolationDetector
import com.bungbungi.app.logic.ViolationEvent
import com.bungbungi.app.signal.NaviLocationSource
import com.bungbungi.app.signal.PleosVehicleSource
import com.bungbungi.app.signal.SignalHub
import com.bungbungi.app.signal.SpeedProbes
import com.bungbungi.app.signal.SpeedSource
import com.bungbungi.app.voice.Lines
import com.bungbungi.app.voice.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 진행 중인 주행 */
data class LiveTrip(
    val startMillis: Long,
    val distanceKm: Double = 0.0,
    val events: List<ViolationEvent> = emptyList(),
    val bonuses: List<BonusEvent> = emptyList(),
    val demoTitle: String? = null,
) {
    val counts: Map<Violation, Int> get() = events.groupingBy { it.type }.eachCount()
}

data class EngineState(
    val now: Long = 0,
    val speedKmh: Float? = null,
    val speedSource: SpeedSource? = null,
    val trip: LiveTrip? = null,
    val trips: List<TripRecord> = emptyList(),
    val avatar: AvatarState = AvatarState(0),
    /** 마지막 위반 시각 (기분 표현용) */
    val lastViolation: ViolationEvent? = null,
    val demoCaption: String? = null,
    /** 아직 보여주지 않은 진화·퇴화 */
    val pendingChange: StageChange? = null,
    val lastFinishedTrip: TripRecord? = null,
    /** 최근 8초 안에 위반이 있었음 (아바타 어지러움, 조명 주황) */
    val upset: Boolean = false,
)

/**
 * 앱의 심장. 신호를 모아 0.25초마다 감지기에 넣고, 주행이 끝나면 점수를 저장하고 아바타를 다시 계산한다.
 * 시연 모드에서는 시나리오 값을 차량 신호 대신 쓰고, 가상 시계로 날짜를 앞당긴다.
 */
class DriveEngine(private val context: Context) {

    val hub = SignalHub()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = Store(context)
    private val vehicle = PleosVehicleSource(context, hub, scope)
    private val probes = SpeedProbes(context, hub, scope)
    private val navi = NaviLocationSource(context, hub)
    val voice = Voice(context, hub)

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private var detector = ViolationDetector()
    private var stoppedSince: Long? = null
    private var movingSince: Long? = null
    private var lastTickReal = 0L
    private var demoJob: Job? = null
    private var demoFrame: com.bungbungi.app.demo.ScenarioFrame? = null
    private var demoKmPerRealMs = 0.0
    private var lastSpokenAt = 0L
    private var lastAmbient = 0

    fun now() = System.currentTimeMillis() + store.clockOffsetMillis

    fun start() {
        val trips = store.loadTrips()
        val avatar = AvatarEngine.evaluate(store.loadAvatar(now()), trips, now())
        store.saveAvatar(avatar)
        _state.value = EngineState(now = now(), trips = trips, avatar = avatar)
        vehicle.start()
        probes.start()
        navi.start()
        voice.start()
        scope.launch {
            delay(2_500)
            voice.say(Lines.greeting(avatar.stage, avatar.born))
        }
        scope.launch {
            while (isActive) {
                tick()
                delay(250)
            }
        }
    }

    fun onPermissionsGranted() {
        probes.startGps()
        navi.startFused()
    }

    private fun tick() {
        val realNow = System.currentTimeMillis()
        val dtMs = if (lastTickReal == 0L) 0L else realNow - lastTickReal
        lastTickReal = realNow
        val t = now()
        val sig = hub.state.value
        val frame = demoFrame

        val (src, speed) = if (frame != null) null to frame.speedKmh
        else sig.bestSpeed(realNow)?.let { it.first to it.second } ?: (null to null)

        val sample = if (frame != null) DriveSample(
            timeMillis = realNow, speedKmh = frame.speedKmh, speedLimitKmh = frame.limitKmh,
            inCautionZone = frame.schoolZone, brakePressed = frame.brake, turnSignalOn = frame.turn != "OFF",
            steeringAngleDeg = frame.steering, seatbeltWarning = frame.seatbeltWarning, cruiseOn = frame.cruise,
        ) else DriveSample(
            timeMillis = realNow, speedKmh = speed, speedLimitKmh = sig.speedLimitKmh,
            inCautionZone = sig.cautionZone != null, brakePressed = sig.brakePressed, turnSignalOn = sig.turnSignalOn,
            steeringAngleDeg = sig.steeringAngleDeg, seatbeltWarning = sig.seatbeltWarning == true, cruiseOn = sig.cruiseOn,
        )

        var trip = _state.value.trip
        val moving = (speed ?: 0f) >= 5f

        // 주행 시작: 5km/h 이상으로 2초 넘게 움직이면 (시연 중에는 시나리오가 직접 시작)
        movingSince = if (moving) movingSince ?: realNow else null
        if (trip == null && frame == null && moving && realNow - (movingSince ?: realNow) >= 2_000 && sig.gear != "PARK") {
            trip = LiveTrip(startMillis = t)
            detector = ViolationDetector()
            hub.log("주행 시작")
        }

        if (trip != null) {
            val km = if (frame != null) demoKmPerRealMs * dtMs else (speed ?: 0f) * dtMs / 3_600_000.0
            val result = detector.process(sample, km)
            trip = trip.copy(
                distanceKm = trip.distanceKm + km,
                events = trip.events + result.violations,
                bonuses = trip.bonuses + result.bonuses,
            )
            result.violations.forEach { onViolation(it) }
            result.bonuses.forEach { b -> hub.log("보너스: ${b.type.label}"); voice.say(Lines.bonus(b.type)) }

            // 주행 종료: P단 또는 20초 이상 정차 (실차 기준). 시연 주행은 시나리오가 끝날 때 종료한다.
            if (frame == null && trip.demoTitle == null) {
                if (!moving) {
                    val since = stoppedSince ?: realNow.also { stoppedSince = it }
                    if (sig.gear == "PARK" || realNow - since > 20_000) {
                        _state.update { it.copy(trip = trip) }
                        finishTrip(); return
                    }
                } else stoppedSince = null
            }
        }

        // 화면 갱신을 줄이려고 시각은 초 단위로만 바꾼다 (에뮬레이터·맥 부하 감소)
        _state.update {
            it.copy(
                now = t - t % 1_000, speedKmh = speed?.let { v -> Math.round(v * 10) / 10f }, speedSource = src, trip = trip,
                demoCaption = frame?.caption,
                lastViolation = trip?.events?.lastOrNull() ?: it.lastViolation,
                // 위반 시각은 실제 시각이라 가상 시계(now)가 아니라 실제 시각과 비교한다
                upset = trip != null && (trip.events.lastOrNull()?.let { e -> realNow - e.atMillis < 8_000 } == true),
            )
        }
        updateAmbient()
    }

    private fun onViolation(e: ViolationEvent) {
        hub.log("위반: ${e.type.label} (${e.detail})")
        val real = System.currentTimeMillis()
        if (real - lastSpokenAt > 4_000) {
            lastSpokenAt = real
            voice.say(Lines.violation(e.type))
        }
    }

    /** 주행 중에는 기분(초록/주황), 정차 중에는 아바타 단계 색 */
    private fun updateAmbient() {
        val s = _state.value
        val color = when {
            s.trip != null && s.upset -> 0xFF8C00
            s.trip != null -> 0x22CC66
            !s.avatar.born -> 0xFFE7A0
            else -> when (s.avatar.stage) {
                Stage.ANGEL -> 0xFFB6E1; Stage.GENTLE -> 0x7CFFB2; Stage.NORMAL -> 0xFFFFFF
                Stage.GRUMPY -> 0xFF9F43; Stage.WILD -> 0xFF2D2D
            }
        }
        if (color != lastAmbient && vehicle.setAmbientColor(color)) lastAmbient = color
    }

    fun finishTrip() {
        val s = _state.value
        val trip = s.trip ?: return
        stoppedSince = null
        if (trip.distanceKm < 0.05 && trip.demoTitle == null) {
            // 거의 움직이지 않은 주행은 기록하지 않는다
            hub.log("짧은 주행(%.2fkm)은 기록하지 않음".format(trip.distanceKm))
            _state.update { it.copy(trip = null, demoCaption = null) }
            return
        }
        val record = TripRecord(
            id = System.currentTimeMillis(), startMillis = trip.startMillis, endMillis = now(),
            distanceKm = trip.distanceKm, counts = trip.counts, bonusCount = trip.bonuses.size,
        )
        val trips = s.trips + record
        store.saveTrips(trips)
        hub.log("주행 종료: %.1fkm, %d점".format(record.distanceKm, record.score))
        _state.update { it.copy(trip = null, trips = trips, lastFinishedTrip = record, demoCaption = null) }
        reevaluate()
        voice.say("수고했어요. 이번 주행은 %.0f킬로미터, %d점이에요.".format(record.distanceKm, record.score))
    }

    private fun reevaluate() {
        val s = _state.value
        val updated = AvatarEngine.evaluate(s.avatar, s.trips, now())
        store.saveAvatar(updated)
        val change = updated.lastChange?.takeIf { it != s.avatar.lastChange }
        _state.update { it.copy(avatar = updated, now = now(), pendingChange = change ?: it.pendingChange) }
        change?.let { voice.say(Lines.change(it.from, it.to)) }
    }

    fun consumeChange() = _state.update { it.copy(pendingChange = null) }

    // ---------------- 시연 ----------------

    fun playScenario(sc: Scenario) {
        if (demoJob?.isActive == true) return
        _state.value.trip?.let { finishTrip() }
        demoKmPerRealMs = sc.nominalKm / sc.durationMillis
        demoJob = scope.launch {
            detector = ViolationDetector()
            _state.update { it.copy(trip = LiveTrip(startMillis = now(), demoTitle = sc.title), lastViolation = null) }
            hub.log("시연 시작: ${sc.title}")
            for (f in sc.frames) {
                demoFrame = f
                delay(sc.stepMillis)
            }
            demoFrame = null
            finishTrip()
        }
    }

    fun stopScenario() {
        demoJob?.cancel()
        demoFrame = null
        if (_state.value.trip != null) finishTrip()
    }

    fun advanceDays(days: Int) {
        store.clockOffsetMillis += days * AvatarEngine.DAY
        hub.log("시간 이동 +${days}일")
        reevaluate()
    }

    /** 하루치 주행을 빠르게 기록 (시연에서 2주를 몇 분으로 압축할 때) */
    fun addQuickDay(rough: Boolean) {
        val t = now()
        val counts = if (rough) mapOf(Violation.SPEEDING to 1, Violation.HARSH_ACCEL to 1, Violation.NO_SIGNAL_LANE_CHANGE to 1)
        else emptyMap()
        val km = if (rough) 25.0 else 30.0
        val rec = TripRecord(t, t - 3_600_000, t, km, counts, if (rough) 0 else 1)
        val trips = _state.value.trips + rec
        store.saveTrips(trips)
        _state.update { it.copy(trips = trips) }
        advanceDays(1)
    }

    fun reset() {
        demoJob?.cancel(); demoFrame = null
        store.clear()
        _state.value = EngineState(now = now(), avatar = AvatarState(now()))
        store.saveAvatar(_state.value.avatar)
        hub.log("기록 초기화")
    }

    fun askMood() {
        voice.listen(
            onText = { text -> hub.log("들은 말: $text"); answerMood() },
            onFail = { err -> hub.log("음성 인식 실패($err) → 바로 대답"); answerMood() },
        )
    }

    fun answerMood() {
        val s = _state.value
        val dayStart = s.now - (s.now % AvatarEngine.DAY)
        val today = s.trips.filter { it.endMillis >= dayStart }
        val score = com.bungbungi.app.logic.Scoring.weightedScore(today)?.toInt()
        voice.say(Lines.mood(score, s.avatar.stage.takeIf { s.avatar.born }, today.sumOf { it.violationTotal }))
    }

    fun release() {
        vehicle.release(); probes.release(); navi.release(); voice.release()
    }
}
