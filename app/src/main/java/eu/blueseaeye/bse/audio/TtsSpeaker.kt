package eu.blueseaeye.bse.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import eu.blueseaeye.bse.data.SettingsStore
import eu.blueseaeye.bse.model.AppSettings
import eu.blueseaeye.bse.model.ReadingOutputMode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Wypowiada komunikaty. Dwa tryby, jak w wersji iOS:
 *  - ARIA (czytnik ekranu): wysyła ogłoszenie do usługi dostępności (odpowiednik
 *    UIAccessibility.post(.announcement)); wtedy TalkBack/Jeshuo je odczytają.
 *  - TTS (synteza): mówi bezpośrednio przez TextToSpeech z ustawionym głosem,
 *    tempem i głośnością.
 */
class TtsSpeaker(private val context: Context) {

    private val appContext = context.applicationContext
    private val accessibilityManager =
        appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var isReady = false

    private val utteranceCounter = AtomicLong(0)

    fun initialize(onReady: (() -> Unit)? = null) {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                tts?.let { engine ->
                    runCatching { engine.language = Locale("pl", "PL") }
                    SettingsStore.applyAvailableVoices(engine)
                }
                onReady?.invoke()
            }
        }
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        isReady = false
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    val screenReaderEnabled: Boolean
        get() = accessibilityManager.isEnabled && accessibilityManager.isTouchExplorationEnabled

    /** Zwykły komunikat: w trybie ARIA idzie do czytnika, inaczej mówi syntezator. */
    suspend fun announce(text: String, settings: AppSettings) {
        if (settings.readingOutput == ReadingOutputMode.ARIA) {
            postAccessibilityAnnouncement(text)
        } else {
            speak(text, settings)
        }
    }

    /** Komunikat krytyczny (utrata/przywrócenie połączenia) — zawsze mówiony głosem. */
    suspend fun announceCritical(text: String, settings: AppSettings) {
        speak(text, settings)
    }

    fun postAccessibilityAnnouncement(text: String) {
        if (!accessibilityManager.isEnabled) return
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
        event.className = TtsSpeaker::class.java.name
        event.packageName = appContext.packageName
        event.text.add(text)
        runCatching { accessibilityManager.sendAccessibilityEvent(event) }
    }

    private suspend fun speak(text: String, settings: AppSettings) {
        val engine = tts ?: return
        if (!isReady) return

        runCatching {
            val voice = settings.readingVoiceIdentifier?.let { id ->
                engine.voices?.firstOrNull { it.name == id }
            }
            if (voice != null) {
                engine.voice = voice
            } else {
                engine.language = Locale("pl", "PL")
            }
            engine.setSpeechRate(mapRate(settings.readingRate))
        }

        val utteranceId = "bse-" + utteranceCounter.incrementAndGet()
        val volume = (settings.readingVolume / 100.0).toFloat().coerceIn(0f, 1f)
        val timeoutMs = speechTimeoutMs(text, settings)

        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Unit> { continuation ->
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                })

                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
                }
                val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                if (result != TextToSpeech.SUCCESS && continuation.isActive) {
                    continuation.resume(Unit)
                }
                continuation.invokeOnCancellation { runCatching { engine.stop() } }
            }
        }
    }

    /** Odwzorowanie procentowego tempa (50–400) na mnożnik TextToSpeech (~0.5–2.5). */
    private fun mapRate(percent: Double): Float {
        val normalized = percent.coerceIn(50.0, 400.0)
        return (normalized / 150.0).toFloat().coerceIn(0.4f, 3.0f)
    }

    private fun speechTimeoutMs(text: String, settings: AppSettings): Long {
        val baseDuration = maxOf(6.0, text.length / 7.0 + 4.0)
        val rateFactor = maxOf(settings.readingRate / 150.0, 0.5)
        val seconds = (baseDuration / rateFactor).coerceIn(4.0, 20.0)
        return (seconds * 1000).toLong()
    }
}
