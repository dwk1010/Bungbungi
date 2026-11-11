package com.bungbungi.app.data

import android.content.Context
import com.bungbungi.app.logic.AvatarPart
import com.bungbungi.app.logic.AvatarState
import com.bungbungi.app.logic.Stage
import com.bungbungi.app.logic.StageChange
import com.bungbungi.app.logic.TripRecord
import com.bungbungi.app.logic.Violation
import org.json.JSONArray
import org.json.JSONObject

/**
 * 주행 기록과 아바타 상태를 차량 안(앱 내부 저장소)에 저장한다.
 * 데이터가 작아서 SharedPreferences + JSON으로 충분하다.
 */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("bungbungi", Context.MODE_PRIVATE)

    var clockOffsetMillis: Long
        get() = prefs.getLong("clockOffset", 0)
        set(v) = prefs.edit().putLong("clockOffset", v).apply()

    var carModel: com.bungbungi.app.logic.CarModel
        get() = runCatching { com.bungbungi.app.logic.CarModel.valueOf(prefs.getString("carModel", "AVANTE")!!) }.getOrDefault(com.bungbungi.app.logic.CarModel.AVANTE)
        set(v) = prefs.edit().putString("carModel", v.name).apply()

    fun loadTrips(): List<TripRecord> {
        val arr = JSONArray(prefs.getString("trips", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val c = o.getJSONObject("counts")
            TripRecord(
                id = o.getLong("id"),
                startMillis = o.getLong("start"),
                endMillis = o.getLong("end"),
                distanceKm = o.getDouble("km"),
                counts = Violation.entries.associateWith { c.optInt(it.name, 0) }.filterValues { it > 0 },
                bonusCount = o.optInt("bonus", 0),
            )
        }
    }

    fun saveTrips(trips: List<TripRecord>) {
        val arr = JSONArray()
        trips.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id); put("start", t.startMillis); put("end", t.endMillis); put("km", t.distanceKm)
                put("bonus", t.bonusCount)
                put("counts", JSONObject().apply { t.counts.forEach { (k, v) -> put(k.name, v) } })
            })
        }
        prefs.edit().putString("trips", arr.toString()).apply()
    }

    fun loadAvatar(defaultInstall: Long): AvatarState {
        val s = prefs.getString("avatar", null) ?: return AvatarState(defaultInstall)
        val o = JSONObject(s)
        val change = o.optJSONObject("change")?.let {
            StageChange(it.optString("from").takeIf { f -> f.isNotEmpty() }?.let(Stage::valueOf), Stage.valueOf(it.getString("to")), it.getLong("at"))
        }
        val col = o.getJSONArray("collected")
        return AvatarState(
            installMillis = o.getLong("install"),
            born = o.getBoolean("born"),
            stage = Stage.valueOf(o.getString("stage")),
            part = AvatarPart.valueOf(o.getString("part")),
            lastEvalMillis = o.getLong("lastEval"),
            resting = o.optBoolean("resting"),
            collected = (0 until col.length()).map { Stage.valueOf(col.getString(it)) }.toSet(),
            lastChange = change,
        )
    }

    fun saveAvatar(a: AvatarState) {
        val o = JSONObject().apply {
            put("install", a.installMillis); put("born", a.born); put("stage", a.stage.name); put("part", a.part.name)
            put("lastEval", a.lastEvalMillis); put("resting", a.resting)
            put("collected", JSONArray(a.collected.map { it.name }))
            a.lastChange?.let { c -> put("change", JSONObject().apply { put("from", c.from?.name ?: ""); put("to", c.to.name); put("at", c.atMillis) }) }
        }
        prefs.edit().putString("avatar", o.toString()).apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
