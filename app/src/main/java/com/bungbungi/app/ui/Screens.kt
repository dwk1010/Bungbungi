package com.bungbungi.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bungbungi.app.DriveEngine
import com.bungbungi.app.EngineState
import com.bungbungi.app.demo.Scenarios
import com.bungbungi.app.logic.AvatarEngine
import com.bungbungi.app.logic.AvatarPart
import com.bungbungi.app.logic.Scoring
import com.bungbungi.app.logic.Stage
import com.bungbungi.app.logic.StageChange
import com.bungbungi.app.signal.SpeedSource
import com.bungbungi.app.signal.VehicleSignals
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

val Bg = Color(0xFF0F1420)
val Card = Color(0xFF1A2233)
val Accent = Color(0xFF7CC4FF)
val Muted = Color(0xFF8A96AD)
val Good = Color(0xFF5BD68A)
val Warn = Color(0xFFFFA24C)

enum class Tab(val label: String) { HOME("붕붕이"), DRIVE("주행"), BOOK("도감"), TRIPS("기록"), SIGNALS("신호"), DEMO("시연") }

@Composable
fun BungApp(engine: DriveEngine, state: EngineState, signals: VehicleSignals, log: List<String>) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    val driving = state.trip != null
    LaunchedEffect(driving) {
        if (driving && tab == Tab.HOME) tab = Tab.DRIVE
        if (!driving && tab == Tab.DRIVE) tab = Tab.HOME
    }
    Row(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
        Column(Modifier.width(150.dp).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("붕붕이", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
            Tab.entries.forEach { t ->
                val sel = t == tab
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (sel) Accent.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { tab = t }.padding(vertical = 14.dp, horizontal = 12.dp)
                ) {
                    Text(t.label + if (t == Tab.DRIVE && driving) " ●" else "", color = if (sel) Accent else Color.White, fontSize = 20.sp)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
            when (tab) {
                Tab.HOME -> HomeScreen(engine, state)
                Tab.DRIVE -> DriveScreen(engine, state)
                Tab.BOOK -> BookScreen(state)
                Tab.TRIPS -> TripsScreen(state)
                Tab.SIGNALS -> SignalsScreen(state, signals, log)
                Tab.DEMO -> DemoScreen(engine, state, log)
            }
            state.pendingChange?.let { ChangeOverlay(it, state.avatar.part) { engine.consumeChange() } }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(Card).padding(20.dp)) { content() }
}

@Composable
private fun Big(text: String, color: Color = Color.White, size: Int = 34) =
    Text(text, color = color, fontSize = size.sp, fontWeight = FontWeight.Bold)

@Composable
private fun Small(text: String, color: Color = Muted, size: Int = 18) = Text(text, color = color, fontSize = size.sp)

@Composable
fun AvatarFor(state: EngineState, modifier: Modifier, mood: Mood = Mood.CALM) {
    val a = state.avatar
    if (!a.born) {
        val total = AvatarEngine.HATCH_DAYS * AvatarEngine.DAY
        val progress = ((state.now - a.installMillis).toFloat() / total).coerceIn(0f, 1f)
        EggAvatar(AvatarEngine.currentScore(state.trips, state.now), progress, modifier)
    } else {
        CarAvatar(a.stage, a.part, if (a.resting) Mood.SLEEPY else mood, modifier)
    }
}

@Composable
private fun HomeScreen(engine: DriveEngine, state: EngineState) {
    val a = state.avatar
    val now = state.now
    val dayStart = now - (now % AvatarEngine.DAY)
    val today = state.trips.filter { it.endMillis >= dayStart }
    val todayScore = Scoring.weightedScore(today)
    val current = AvatarEngine.currentScore(state.trips, now)
    val daysLeft = ceil((AvatarEngine.nextEvalMillis(a) - now).toDouble() / AvatarEngine.DAY).toInt().coerceAtLeast(0)

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel(Modifier.weight(1.2f).fillMaxHeight()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Big(if (a.born) "${a.stage.label} 붕붕이" else "알 속의 붕붕이", size = 40)
                Small(
                    when {
                        !a.born -> "운전 습관을 먹으며 자라는 중이에요"
                        a.resting -> "이번 주는 거의 안 달려서 쉬는 중이에요"
                        else -> a.stage.look
                    }
                )
                AvatarFor(state, Modifier.fillMaxWidth().weight(1f), if ((todayScore ?: 100.0) >= 90) Mood.HAPPY else Mood.CALM)
                if (a.part != AvatarPart.NONE) Small("눈에 띄는 습관: ${a.part.label}", Warn)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { engine.askMood() }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text("🎤  붕붕아, 오늘 기분 어때?", fontSize = 20.sp, color = Color.Black)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Small("오늘 주행")
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Stat("거리", "%.1f km".format(today.sumOf { it.distanceKm }))
                        Stat("위반", "${today.sumOf { it.violationTotal }}회")
                        Stat("점수", todayScore?.let { "${it.toInt()}점" } ?: "-")
                    }
                    state.lastFinishedTrip?.let { t ->
                        Spacer(Modifier.height(8.dp))
                        Small("방금 주행: %.1fkm · %d점 · %s".format(t.distanceKm, t.score,
                            if (t.counts.isEmpty()) "위반 없음" else t.counts.entries.joinToString { "${it.key.label} ${it.value}" }))
                    }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Small(if (a.born) "다음 변화까지" else "태어나기까지")
                    Big("${daysLeft}일 남음")
                    Spacer(Modifier.height(8.dp))
                    Small("최근 2주 점수 (거리 가중)")
                    val score = current ?: 0.0
                    Big(current?.let { "${it.toInt()}점" } ?: "아직 기록 없음", if (score >= 75) Good else if (score >= 60) Color.White else Warn)
                    if (current != null) {
                        val target = Stage.forScore(current)
                        Small(
                            if (!a.born) "지금 태어나면 ${target.label} 붕붕이예요"
                            else when {
                                target.ordinal > a.stage.ordinal -> "이대로면 ${Stage.entries[a.stage.ordinal + 1].label}(으)로 진화해요"
                                target.ordinal < a.stage.ordinal -> "이대로면 ${Stage.entries[a.stage.ordinal - 1].label}(으)로 변해요"
                                else -> "이대로면 ${a.stage.label} 단계를 유지해요"
                            }
                        )
                        val next = Stage.entries.getOrNull(target.ordinal + 1)
                        if (next != null) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { ((score - target.minScore) / (next.minScore - target.minScore)).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), color = Good,
                            )
                            Small("${next.label}까지 ${(next.minScore - score).toInt()}점")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) = Column {
    Small(label, size = 16)
    Big(value, size = 30)
}

@Composable
private fun DriveScreen(engine: DriveEngine, state: EngineState) {
    val trip = state.trip
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel(Modifier.weight(1.3f).fillMaxHeight()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Big(if (state.upset) "어지러워요…" else if (trip != null) "기분 좋아요" else "정차 중", if (state.upset) Warn else Good, 40)
                Small("주행 중에는 화면을 보지 않아도 돼요. 붕붕이가 목소리와 실내 조명으로 알려줘요.")
                AvatarFor(state, Modifier.fillMaxWidth().weight(1f), if (state.upset) Mood.DIZZY else Mood.HAPPY)
                state.demoCaption?.let { Text("시연: $it", color = Accent, fontSize = 22.sp) }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Stat("속도", state.speedKmh?.let { "${it.toInt()} km/h" } ?: "-")
                    Stat("거리", "%.1f km".format(trip?.distanceKm ?: 0.0))
                    Stat("위반", "${trip?.events?.size ?: 0}회")
                }
            }
            Panel(Modifier.fillMaxWidth().weight(1f)) {
                Column {
                    Small("이번 주행에서 붕붕이가 느낀 것")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(trip?.events?.reversed() ?: emptyList()) { e ->
                            Text("⚠ ${e.type.label} · ${e.detail}", color = Warn, fontSize = 18.sp)
                        }
                        items(trip?.bonuses ?: emptyList()) { b -> Text("★ ${b.type.label}", color = Good, fontSize = 18.sp) }
                    }
                }
            }
            if (trip != null) OutlinedButton(onClick = { if (trip.demoTitle != null) engine.stopScenario() else engine.finishTrip() }) {
                Text("주행 끝내기", fontSize = 18.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun BookScreen(state: EngineState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Big("붕붕이 도감")
        Small("지금까지 만나 본 모습 ${state.avatar.collected.size} / ${Stage.entries.size}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stage.entries.reversed().forEach { st ->
                val got = st in state.avatar.collected
                Panel(Modifier.weight(1f).height(360.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Big(if (got) st.label else "???", size = 26)
                        Small("${st.minScore}점 이상", size = 15)
                        Box(Modifier.fillMaxWidth().weight(1f).alpha(if (got) 1f else 0.15f)) {
                            CarAvatar(st, AvatarPart.NONE, Mood.CALM, Modifier.fillMaxSize())
                        }
                        Small(if (got) st.look else "아직 못 만났어요", size = 14)
                    }
                }
            }
        }
    }
}

@Composable
private fun TripsScreen(state: EngineState) {
    val fmt = remember { SimpleDateFormat("M/d HH:mm", Locale.KOREA) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Big("주행 기록")
        Small("총 ${state.trips.size}회 · %.1fkm".format(state.trips.sumOf { it.distanceKm }))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.trips.reversed()) { t ->
                Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fmt.format(Date(t.endMillis)) + " · %.1fkm".format(t.distanceKm), color = Color.White, fontSize = 20.sp)
                            Small(if (t.counts.isEmpty()) "위반 없음" else t.counts.entries.joinToString { "${it.key.label} ${it.value}회" })
                        }
                        Big("${t.score}점", if (t.score >= 75) Good else if (t.score >= 60) Color.White else Warn, 28)
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalsScreen(state: EngineState, s: VehicleSignals, log: List<String>) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1.4f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Big("속도는 어디서 읽히나?", size = 26)
                    Small("지금 쓰는 속도: " + (state.speedSource?.label?.let { "$it · ${state.speedKmh?.toInt()} km/h" } ?: "없음 (시연 중이면 시나리오 값)"), Accent)
                    SpeedSource.entries.forEach { src ->
                        val r = s.speeds[src]
                        Row(Modifier.fillMaxWidth().border(1.dp, Color(0xFF2B3550), RoundedCornerShape(10.dp)).padding(10.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(src.label, color = Color.White, fontSize = 18.sp)
                                Text(src.api, color = Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                Text("상태: ${r?.status} · 갱신 ${r?.updates ?: 0}회" + (r?.raw?.let { " · 원본값 $it" } ?: ""), color = Muted, fontSize = 14.sp)
                            }
                            Big(r?.kmh?.let { "%.1f".format(it) } ?: "-", if (r?.kmh != null) Good else Muted, 28)
                        }
                    }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Big("차량·내비 신호", size = 24)
                    listOf(
                        "Vehicle SDK" to s.vehicleStatus, "NaviHelper" to s.naviStatus, "FusedLocation" to s.locationStatus,
                        "브레이크" to (s.brakePressed?.let { if (it) "밟음" else "뗌" } ?: "-"),
                        "방향지시등" to (s.turnSignal ?: "-"),
                        "핸들 각도" to (s.steeringAngleDeg?.let { "%.1f°".format(it) } ?: "-"),
                        "주행거리계" to (s.odometerKm?.let { "%.1f km".format(it) } ?: "-"),
                        "안전벨트 경고" to (s.seatbeltWarning?.let { if (it) "켜짐" else "꺼짐" } ?: "-"),
                        "크루즈" to (s.cruise ?: "-"), "기어" to (s.gear ?: "-"),
                        "제한속도" to (s.speedLimitKmh?.let { "$it km/h" } ?: "-"),
                        "주의구간" to (s.cautionZone ?: "없음"),
                    ).forEach { (k, v) ->
                        Row { Text(k, color = Muted, fontSize = 17.sp, modifier = Modifier.width(160.dp)); Text(v, color = Color.White, fontSize = 17.sp) }
                    }
                }
            }
        }
        LogPanel(log, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun LogPanel(log: List<String>, modifier: Modifier) {
    Panel(modifier) {
        Column {
            Small("기록")
            LazyColumn { items(log) { Text(it, color = Color(0xFFC9D3E6), fontSize = 14.sp, fontFamily = FontFamily.Monospace) } }
        }
    }
}

@Composable
private fun DemoScreen(engine: DriveEngine, state: EngineState, log: List<String>) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd (E)", Locale.KOREA) }
    val playing = state.trip?.demoTitle != null
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1.3f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Big("주행 시나리오 재생", size = 26)
                    Small("실제 차량 신호 대신 미리 만든 주행을 감지기에 넣어요. 약 45초 동안 재생돼요.")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DemoButton("안전 주행 (20km)", Good, !playing) { engine.playScenario(Scenarios.safe) }
                        DemoButton("난폭 주행 (10km)", Warn, !playing) { engine.playScenario(Scenarios.rough) }
                        if (playing) OutlinedButton(onClick = { engine.stopScenario() }) { Text("중지", color = Color.White, fontSize = 18.sp) }
                    }
                    state.demoCaption?.let { Text("▶ $it", color = Accent, fontSize = 20.sp) }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Big("시간 가속", size = 26)
                    Small("가상 날짜: ${fmt.format(Date(state.now))} · 설치 후 ${((state.now - state.avatar.installMillis) / AvatarEngine.DAY)}일째")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DemoButton("+1일", Accent) { engine.advanceDays(1) }
                        DemoButton("+1주", Accent) { engine.advanceDays(7) }
                    }
                    Small("하루치 주행을 바로 기록하고 하루를 넘겨요 (2주를 몇 번의 클릭으로)")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DemoButton("안전한 하루", Good) { engine.addQuickDay(rough = false) }
                        DemoButton("거친 하루", Warn) { engine.addQuickDay(rough = true) }
                        DemoButton("안전 7일", Good) { repeat(7) { engine.addQuickDay(rough = false) } }
                        DemoButton("거친 7일", Warn) { repeat(7) { engine.addQuickDay(rough = true) } }
                    }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    DemoButton("음성 대답 듣기", Accent) { engine.answerMood() }
                    OutlinedButton(onClick = { engine.reset() }) { Text("기록 초기화", color = Color.White, fontSize = 18.sp) }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel(Modifier.fillMaxWidth().height(300.dp)) { AvatarFor(state, Modifier.fillMaxSize(), if (state.upset) Mood.DIZZY else Mood.CALM) }
            LogPanel(log, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun DemoButton(text: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) =
    Button(onClick = onClick, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = color)) {
        Text(text, color = Color.Black, fontSize = 18.sp)
    }

/** 진화·퇴화·탄생 연출 */
@Composable
private fun ChangeOverlay(change: StageChange, part: AvatarPart, onDone: () -> Unit) {
    var show by remember(change) { mutableStateOf(false) }
    var revealed by remember(change) { mutableStateOf(false) }
    LaunchedEffect(change) {
        show = true
        kotlinx.coroutines.delay(1_600)
        revealed = true
    }
    val flash by animateFloatAsState(if (revealed) 0f else 1f, tween(900), label = "flash")
    AnimatedVisibility(show, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).clickable { if (revealed) onDone() }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Big(
                    when {
                        change.isBirth -> "붕붕이가 태어났어요!"
                        change.isUp -> "진화! ${change.from?.label} → ${change.to.label}"
                        else -> "퇴화… ${change.from?.label} → ${change.to.label}"
                    }, if (change.isBirth || change.isUp) Good else Warn, 44
                )
                Box(Modifier.size(620.dp, 460.dp)) {
                    if (!revealed) {
                        if (change.isBirth) EggAvatar(80.0, 1f, Modifier.fillMaxSize())
                        else CarAvatar(change.from!!, AvatarPart.NONE, Mood.CALM, Modifier.fillMaxSize().alpha(0.4f + 0.6f * flash))
                    } else {
                        CarAvatar(change.to, part, if (change.isBirth || change.isUp) Mood.HAPPY else Mood.CALM, Modifier.fillMaxSize())
                    }
                }
                Small(if (revealed) "${change.to.look} · 화면을 누르면 닫혀요" else "두근두근…", size = 20)
            }
        }
    }
}
