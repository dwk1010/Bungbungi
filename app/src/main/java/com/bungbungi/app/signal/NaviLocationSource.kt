package com.bungbungi.app.signal

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.content.pm.PackageManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** NaviHelper(제한속도·주의구간·단속카메라)와 FusedLocation(위치 → 속도 보조) */
class NaviLocationSource(private val context: Context, private val hub: SignalHub) {

    private val navi = NaviHelper(context.applicationContext)
    private val fused = FusedLocation(context.applicationContext)
    private var lastFix: LocationData? = null
    private var fusedRegistered = false

    private val naviListener = object : NaviHelperEventListener {
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
            hub.update { it.copy(speedLimitKmh = speedLimit.takeIf { v -> v > 0 }) }
        }

        override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) {
            val zone = cautionPointList.firstOrNull { it.type in ZONE_TYPES && it.remainDistance <= 300 }
            hub.update { it.copy(cautionZone = zone?.type?.name) }
        }

        override fun onCameraAlert(cameraAlertList: List<CameraAlert>) {
            val near = cameraAlertList.minByOrNull { it.remainDistance }
            hub.update { s ->
                s.copy(
                    cameraOverSpeed = cameraAlertList.any { it.isOverSpeed || it.isOverSectionAvrSpeed },
                    speedLimitKmh = s.speedLimitKmh ?: near?.speedLimit?.takeIf { it > 0 },
                )
            }
        }

        override fun onError(errorCode: NaviErrorCode) = hub.log("내비 오류: $errorCode")
    }

    private val fusedListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location ?: return
            val prev = lastFix
            lastFix = location
            val raw = "%.5f, %.5f".format(location.latitude, location.longitude)
            if (prev == null || location.timestampMillis <= prev.timestampMillis) {
                hub.speed(SpeedSource.FUSED_LOCATION, null, "위치 수신", raw); return
            }
            val meters = distance(prev.latitude, prev.longitude, location.latitude, location.longitude)
            val sec = (location.timestampMillis - prev.timestampMillis) / 1000.0
            hub.speed(SpeedSource.FUSED_LOCATION, (meters / sec * 3.6).toFloat(), "계산됨", raw)
        }

        override fun onError(exception: Exception) = hub.speed(SpeedSource.FUSED_LOCATION, null, "오류: ${exception.message}")
    }

    fun start() {
        try {
            navi.addListener(naviListener)
            navi.initialize(
                onConnected = { hub.update { it.copy(naviStatus = "연결됨") } },
                onFailed = { e -> hub.update { it.copy(naviStatus = "연결 실패: ${e.message}") } },
            )
            hub.update { it.copy(naviStatus = "연결 중") }
        } catch (e: Exception) {
            hub.update { it.copy(naviStatus = "초기화 실패: ${e.message}") }
        }
        startFused()
    }

    /** FUSED_LOCATION은 dangerous 권한이라 허용된 뒤에 다시 부른다. */
    fun startFused() {
        if (fusedRegistered) return
        try {
            if (!fused.isSupported()) {
                hub.update { it.copy(locationStatus = "FusedLocation 서비스 없음") }; return
            }
            fused.initialize()
            if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                hub.update { it.copy(locationStatus = "권한 필요") }
                hub.speed(SpeedSource.FUSED_LOCATION, null, "권한 필요"); return
            }
            fused.registerFusedLocationCallback(fusedListener)
            fusedRegistered = true
            hub.update { it.copy(locationStatus = "구독 중") }
            hub.speed(SpeedSource.FUSED_LOCATION, null, "구독 중")
        } catch (e: Exception) {
            hub.update { it.copy(locationStatus = "실패: ${e.message}") }
        }
    }

    fun release() {
        runCatching { navi.removeListener(naviListener); navi.release() }
        runCatching { if (fusedRegistered) fused.unregisterFusedLocationCallback(fusedListener); fused.release() }
    }

    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    companion object {
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
        val ZONE_TYPES = setOf(CautionPointType.SCHOOL_ZONE)
    }
}
