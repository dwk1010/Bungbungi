package com.bungbungi.app.signal

import ai.pleos.playground.vehicle.IVehicleService
import ai.pleos.playground.vehicle.callback.IGetPropertyResult
import ai.pleos.playground.vehicle.callback.IPropertyCallback
import android.annotation.SuppressLint
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Vehicle SDK 2.3.2 공개 API에는 차량 속도가 없다. 그래서 속도를 얻을 수 있는 경로를 모두 열어 두고
 * 에뮬레이터에서 실제로 값이 들어오는 것을 쓴다.
 *
 * 1) Pleos 차량 서비스(IVehicleService)에 PERF_VEHICLE_SPEED 속성을 직접 요청 (SDK 내부 속성 목록에 있음)
 * 2) AAOS CarPropertyManager PERF_VEHICLE_SPEED
 * 3) GPS Location.speed
 */
class SpeedProbes(private val context: Context, private val hub: SignalHub, private val scope: CoroutineScope) {

    // ---------- 1) Pleos 차량 서비스 직접 바인딩 ----------
    private var pleosService: IVehicleService? = null
    private var pollJob: Job? = null
    private val propertyCallback = object : IPropertyCallback.Stub() {
        override fun onChangeEvent(prop: String?, areaId: Int, value: String?) = onPleosValue(value, "구독")
        override fun onErrorEvent(prop: String?, error: String?) = hub.speed(SpeedSource.PLEOS_PROPERTY, null, "오류: $error")
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = IVehicleService.Stub.asInterface(binder)
            pleosService = svc
            hub.speed(SpeedSource.PLEOS_PROPERTY, null, "연결됨")
            // 이 속성이 서비스의 공개 목록(capability)에 있는지 기록으로 남긴다
            runCatching {
                svc.getCapabilityConfigByKey(PROP, object : ai.pleos.playground.vehicle.callback.ICapabilityConfigResult.Stub() {
                    override fun onResult(r: String?) = hub.log("PERF_VEHICLE_SPEED capability: ${r?.take(200)}")
                    override fun onFailed(t: String?) = hub.log("PERF_VEHICLE_SPEED capability 실패: $t")
                })
            }.onFailure { hub.log("capability 조회 오류: ${it.message}") }
            try {
                svc.registerPropertyCallback(context.packageName, PROP, AREA_GLOBAL, propertyCallback)
            } catch (e: Exception) {
                hub.speed(SpeedSource.PLEOS_PROPERTY, null, "구독 실패: ${e.message}")
            }
            pollJob?.cancel()
            pollJob = scope.launch {
                while (isActive) {
                    try {
                        svc.getPropertyValue(PROP, AREA_GLOBAL, 0, object : IGetPropertyResult.Stub() {
                            override fun onResult(key: String?, areaId: Int, value: String?) = onPleosValue(value, "조회")
                            override fun onFailed(key: String?, areaId: Int, error: String?) =
                                hub.speed(SpeedSource.PLEOS_PROPERTY, null, "조회 실패: $error")
                        })
                    } catch (e: Exception) {
                        hub.speed(SpeedSource.PLEOS_PROPERTY, null, "조회 오류: ${e.message}")
                    }
                    delay(500)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            pleosService = null
            pollJob?.cancel()
            hub.speed(SpeedSource.PLEOS_PROPERTY, null, "연결 끊김")
        }
    }

    private fun onPleosValue(value: String?, how: String) {
        val mps = value?.trim()?.trim('"')?.toFloatOrNull()
        // AAOS PERF_VEHICLE_SPEED 단위는 m/s
        hub.speed(SpeedSource.PLEOS_PROPERTY, mps?.let { it * 3.6f }, "$how 성공", value)
    }

    // ---------- 2) AAOS CarPropertyManager ----------
    private var car: Car? = null
    private var carCallback: CarPropertyManager.CarPropertyEventCallback? = null

    // ---------- 3) GPS ----------
    private val gpsListener = LocationListener { loc: Location ->
        if (loc.hasSpeed()) hub.speed(SpeedSource.GPS, loc.speed * 3.6f, "수신", "%.5f, %.5f".format(loc.latitude, loc.longitude))
        else hub.speed(SpeedSource.GPS, null, "위치만 수신(속도 없음)")
    }

    fun start() {
        startPleos()
        startCarProperty()
        startGps()
    }

    private fun startPleos() {
        val intent = Intent(ACTION).apply { component = ComponentName(PKG, SERVICE) }
        val ok = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }.getOrElse {
            hub.speed(SpeedSource.PLEOS_PROPERTY, null, "바인딩 오류: ${it.message}"); return
        }
        hub.speed(SpeedSource.PLEOS_PROPERTY, null, if (ok) "연결 중" else "바인딩 실패")
    }

    private fun startCarProperty() {
        try {
            val c = Car.createCar(context) ?: run { hub.speed(SpeedSource.CAR_PROPERTY, null, "Car 서비스 없음"); return }
            car = c
            val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            val cb = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    val mps = (value.value as? Number)?.toFloat()
                    hub.speed(SpeedSource.CAR_PROPERTY, mps?.let { it * 3.6f }, "구독 성공", value.value?.toString())
                }
                override fun onErrorEvent(propId: Int, zone: Int) = hub.speed(SpeedSource.CAR_PROPERTY, null, "오류 이벤트")
            }
            carCallback = cb
            val ok = pm.registerCallback(cb, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
            hub.speed(SpeedSource.CAR_PROPERTY, null, if (ok) "구독 중" else "구독 거부")
        } catch (e: Throwable) {
            hub.speed(SpeedSource.CAR_PROPERTY, null, "실패: ${e.javaClass.simpleName} ${e.message ?: ""}".trim())
        }
    }

    @SuppressLint("MissingPermission")
    fun startGps() {
        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            hub.speed(SpeedSource.GPS, null, "위치 권한 없음"); return
        }
        try {
            val lm = context.getSystemService(LocationManager::class.java)
            lm.removeUpdates(gpsListener)
            val provider = if (lm.allProviders.contains(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.FUSED_PROVIDER
            lm.requestLocationUpdates(provider, 500L, 0f, gpsListener, Looper.getMainLooper())
            hub.speed(SpeedSource.GPS, null, "$provider 구독 중")
        } catch (e: Exception) {
            hub.speed(SpeedSource.GPS, null, "실패: ${e.message}")
        }
    }

    fun release() {
        pollJob?.cancel()
        pleosService?.let { runCatching { it.unregisterPropertyCallback(context.packageName, PROP, AREA_GLOBAL, propertyCallback) } }
        runCatching { context.unbindService(connection) }
        car?.let { c ->
            carCallback?.let { cb -> runCatching { (c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager).unregisterCallback(cb) } }
            runCatching { c.disconnect() }
        }
        runCatching { context.getSystemService(LocationManager::class.java).removeUpdates(gpsListener) }
    }

    companion object {
        const val PROP = "PERF_VEHICLE_SPEED"
        const val AREA_GLOBAL = 0
        const val PKG = "ai.pleos.playground.service.vehicle"
        const val SERVICE = "ai.pleos.playground.service.vehicle.VehicleService"
        const val ACTION = "ai.pleos.playground.vehicle.VEHICLE_START"
    }
}
