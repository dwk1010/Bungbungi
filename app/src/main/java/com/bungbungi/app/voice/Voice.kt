package com.bungbungi.app.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.speech.tts.TextToSpeech as AndroidTts
import com.bungbungi.app.signal.SignalHub
import java.util.Locale

/**
 * 아바타 목소리. Pleos TextToSpeech(ON_DEVICE)를 먼저 쓰고, 안 되면 Android 기본 TTS로 말한다.
 * 음성 질문은 Pleos SpeechToText(ON_DEVICE)로 받는다.
 */
class Voice(private val context: Context, private val hub: SignalHub) {

    private var pleosTts: TextToSpeech? = null
    private var pleosReady = false
    private var androidTts: AndroidTts? = null
    private var androidReady = false
    private var stt: SpeechToText? = null
    var status = "준비 중"; private set

    fun start() {
        try {
            val tts = TextToSpeech(context.applicationContext, ai.pleos.playground.tts.constant.Mode.ON_DEVICE)
            pleosTts = tts
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() { pleosReady = true; status = "Pleos TTS"; hub.log("Pleos TTS 준비됨") }
                override fun onFailure(error: ai.pleos.playground.tts.constant.ErrorType) {
                    hub.log("Pleos TTS 실패($error) → 기본 TTS 사용"); startAndroidTts()
                }
            })
        } catch (e: Throwable) {
            hub.log("Pleos TTS 오류: ${e.message} → 기본 TTS 사용"); startAndroidTts()
        }
    }

    private fun startAndroidTts() {
        if (androidTts != null) return
        androidTts = AndroidTts(context.applicationContext) { code ->
            androidReady = code == AndroidTts.SUCCESS
            androidTts?.language = Locale.KOREAN
            status = if (androidReady) "Android TTS" else "TTS 없음"
        }
    }

    fun say(text: String) {
        hub.log("🗣 $text")
        try {
            if (pleosReady) { pleosTts?.speak(text, true); return }
        } catch (e: Throwable) {
            hub.log("Pleos TTS 말하기 실패: ${e.message}"); pleosReady = false; startAndroidTts()
        }
        if (androidTts == null) startAndroidTts()
        androidTts?.speak(text, AndroidTts.QUEUE_FLUSH, null, "bung")
    }

    /** 음성 질문 받기. 인식되면 onText, 실패하면 onFail */
    fun listen(onText: (String) -> Unit, onFail: (String) -> Unit) {
        try {
            val s = stt ?: SpeechToText(context.applicationContext, ai.pleos.playground.stt.constant.Mode.ON_DEVICE).also {
                it.initialize()
                stt = it
            }
            s.addListener(object : ResultListener {
                override fun onUpdated(text: String, isFinal: Boolean) {
                    if (isFinal) { s.removeListener(this); onText(text) }
                }
                override fun onUpdatedEpdData(p0: Long, p1: Long) {}
                override fun onStartedRecognition() { hub.log("듣는 중…") }
                override fun onEndedRecognition() {}
                override fun onError(error: ai.pleos.playground.stt.constant.ErrorType) { s.removeListener(this); onFail(error.toString()) }
                override fun onReady() {}
                override fun onUpdatedRms(p0: Double) {}
            })
            s.request()
        } catch (e: Throwable) {
            onFail(e.message ?: e.javaClass.simpleName)
        }
    }

    fun release() {
        runCatching { pleosTts?.release() }
        runCatching { stt?.release() }
        runCatching { androidTts?.shutdown() }
    }
}
