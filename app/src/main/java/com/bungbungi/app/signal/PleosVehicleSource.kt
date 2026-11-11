package com.bungbungi.app.signal

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatBeltNotBuckledWarningListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pleos Vehicle SDK로 브레이크·방향지시등·핸들 각도·주행거리·안전벨트·크루즈·기어를 구독하고,
 * 앰비언트 라이트 색을 바꾼다.
 */
class PleosVehicleSource(context: Context, private val hub: SignalHub, private val scope: CoroutineScope) {

    private val vehicle = Vehicle(context.applicationContext)
    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)
    private var initialized = false
    private val pending = linkedMapOf<String, () -> Unit>()
    private var ambientAreas: List<SeatArea> = emptyList()
    private var lastOdo: Pair<Long, Float>? = null

    private val brake = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) = hub.update { it.copy(brakePressed = status) }
        override fun onFailed(e: Exception) = hub.log("브레이크 오류: ${e.message}")
    }
    private val turn = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = hub.update { it.copy(turnSignal = state?.name) }
        override fun onFailed(e: Exception) = hub.log("방향지시등 오류: ${e.message}")
    }
    private val steering = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) = hub.update { it.copy(steeringAngleDeg = angle) }
        override fun onFailed(e: Exception) = hub.log("핸들 각도 오류: ${e.message}")
    }
    private val odometer = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            hub.update { it.copy(odometerKm = value) }
            if (value == null) return
            val now = System.currentTimeMillis()
            val prev = lastOdo
            lastOdo = now to value
            if (prev != null && now > prev.first) {
                val kmh = (value - prev.second) / ((now - prev.first) / 3_600_000f)
                if (kmh in 0f..300f) hub.speed(SpeedSource.ODOMETER, kmh, "계산됨", "%.2f km".format(value))
            }
        }
        override fun onFailed(e: Exception) = hub.log("주행거리 오류: ${e.message}")
    }
    private val seatbelt = object : SeatBeltNotBuckledWarningListener {
        override fun onSeatBeltNotBuckledWarningUpdated(state: Boolean?) = hub.update { it.copy(seatbeltWarning = state) }
        override fun onFailed(e: Exception) = hub.log("안전벨트 오류: ${e.message}")
    }
    private val cruise = object : CruiseControlStateListener {
        override fun onCruiseControlStateUpdated(state: CruiseControl?) = hub.update { it.copy(cruise = state?.name) }
        override fun onFailed(e: Exception) = hub.log("크루즈 오류: ${e.message}")
    }
    private val gear = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = hub.update { it.copy(gear = gear?.name?.removePrefix("GEAR_")) }
        override fun onFailed(e: Exception) = hub.log("기어 오류: ${e.message}")
    }

    fun start() {
        try {
            vehicle.initialize()
            initialized = true
            hub.update { it.copy(vehicleStatus = "연결 중") }
        } catch (e: Exception) {
            hub.update { it.copy(vehicleStatus = "초기화 실패: ${e.message}") }
            return
        }
        pending["브레이크"] = { vehicle.getBrake().registerBrakePedalStatus(brake) }
        pending["방향지시등"] = { vehicle.getTurnSignal().registerTurnSignalState(turn) }
        pending["핸들 각도"] = { vehicle.getSteeringWheel().registerSteeringWheelAngle(steering) }
        pending["주행거리"] = { vehicle.getOdometer().registerDistanceDriven(odometer) }
        pending["안전벨트"] = { vehicle.getSafety().registerSeatBeltNotBuckledWarning(driverSeat, seatbelt) }
        pending["크루즈"] = { vehicle.getDrivingMode().registerCruiseControlState(cruise) }
        pending["기어"] = { vehicle.getGear().registerGearSelection(gear) }
        pending["현재값 조회"] = {
            vehicle.getBrake().getCurrentBrakePedalStatus({ v -> hub.update { it.copy(brakePressed = v) } }, { hub.log("브레이크 조회 실패: ${it.message}") })
            vehicle.getTurnSignal().getCurrentTurnSignalState({ v -> hub.update { it.copy(turnSignal = v?.name) } }, { hub.log("방향지시등 조회 실패: ${it.message}") })
            vehicle.getSteeringWheel().getCurrentSteeringWheelAngle({ v -> hub.update { it.copy(steeringAngleDeg = v) } }, { hub.log("핸들 조회 실패: ${it.message}") })
            vehicle.getOdometer().getCurrentDistanceDriven({ v -> hub.update { it.copy(odometerKm = v) } }, { hub.log("주행거리 조회 실패: ${it.message}") })
            vehicle.getGear().getCurrentGearSelectionState({ v -> hub.update { it.copy(gear = v?.name?.removePrefix("GEAR_")) } }, { hub.log("기어 조회 실패: ${it.message}") })
            vehicle.getSafety().hasSeatBeltNotBuckledWarning(driverSeat, { v -> hub.update { it.copy(seatbeltWarning = v) } }, { hub.log("안전벨트 조회 실패: ${it.message}") })
        }
        pending["앰비언트 라이트 영역"] = {
            vehicle.getLight().checkAmbientLightBrightnessColorCapabilities(
                onComplete = { _, areas -> ambientAreas = areas; hub.log("앰비언트 라이트 영역 ${areas.size}개") },
                onFailed = { hub.log("앰비언트 라이트 미지원: ${it.message}") },
            )
        }
        // 서비스 바인딩이 끝나기 전에는 등록이 실패하므로, 성공할 때까지 1초마다 다시 시도한다.
        scope.launch {
            repeat(30) {
                val iter = pending.entries.iterator()
                while (iter.hasNext()) {
                    val (name, action) = iter.next()
                    try {
                        action(); iter.remove(); hub.log("$name 구독 시작")
                    } catch (_: Exception) { }
                }
                if (pending.isEmpty()) {
                    hub.update { it.copy(vehicleStatus = "연결됨") }
                    return@launch
                }
                delay(1_000)
            }
            hub.update { it.copy(vehicleStatus = "일부 실패: ${pending.keys.joinToString()}") }
        }
    }

    /** 실내 앰비언트 라이트 색 (ARGB) */
    /** 영역을 아직 모르면 false를 돌려줘서 다음 틱에 다시 시도하게 한다. */
    fun setAmbientColor(argb: Int): Boolean {
        if (!initialized || ambientAreas.isEmpty()) return false
        val light = vehicle.getLight()
        ambientAreas.forEach { area ->
            try {
                light.setAmbientLightSwitch(area, LightSwitch.ON, { _, _ -> }, { })
                light.setAmbientLightBrightnessColor(area, argb or (0xFF shl 24), { _, _ -> }, { hub.log("조명 색 변경 실패: ${it.message}") })
            } catch (e: Exception) {
                hub.log("조명 색 변경 오류: ${e.message}")
            }
        }
        return true
    }

    fun release() {
        if (!initialized) return
        runCatching { vehicle.release() }
        initialized = false
    }
}
