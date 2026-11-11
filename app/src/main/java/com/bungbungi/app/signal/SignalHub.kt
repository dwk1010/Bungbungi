package com.bungbungi.app.signal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 모든 신호 소스가 쓰는 공용 상태. SDK 콜백은 아무 스레드에서 와도 된다. */
class SignalHub {
    private val _state = MutableStateFlow(VehicleSignals())
    val state: StateFlow<VehicleSignals> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    fun update(block: (VehicleSignals) -> VehicleSignals) = _state.update(block)

    fun speed(src: SpeedSource, kmh: Float?, status: String, raw: String? = null) {
        val before = _state.value.speeds[src]
        if (kmh != null && before?.kmh == null) log("속도 첫 수신: ${src.label} = %.1f km/h (원본 $raw)".format(kmh))
        if (kmh == null && before?.status != status) log("속도 경로 ${src.label}: $status")
        updateSpeed(src, kmh, status, raw)
    }

    private fun updateSpeed(src: SpeedSource, kmh: Float?, status: String, raw: String?) = _state.update { s ->
        val prev = s.speeds[src] ?: SpeedReading()
        val now = System.currentTimeMillis()
        s.copy(speeds = s.speeds + (src to SpeedReading(kmh ?: prev.kmh, if (kmh != null) now else prev.atMillis, status, raw ?: prev.raw, prev.updates + if (kmh != null) 1 else 0)))
    }

    fun log(msg: String) {
        android.util.Log.i("Bungbungi", msg)
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.KOREA).format(java.util.Date())
        _log.update { (listOf("$time  $msg") + it).take(80) }
    }
}
