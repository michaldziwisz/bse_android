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
 *
 * SAMONAPRAWA: TextToSpeech to powiązanie z zewnętrzną usługą systemową
 * (np. silnik Google). Po wielu godzinach pracy system potrafi ubić tę usługę
 * albo powiązanie pada — wtedy engine.speak() cicho zwraca błąd i aplikacja
 * milknie mimo że „działa”. Dlatego wykrywamy nieudaną wypowiedź i odbudowujemy
 * silnik od zera, po czym ponawiamy komunikat raz. Kluczowe dla bezpieczeństwa
 * niewidomego żeglarza: brak głosu = brak informacji o kursie/sterze.
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
            speakWithRecovery(text, settings)
        }
    }

    /** Komunikat krytyczny (utrata/przywrócenie połączenia) — zawsze mówiony głosem. */
    suspend fun announceCritical(text: String, settings: AppSettings) {
        speakWithRecovery(text, settings)
    }

    fun postAccessibilityAnnouncement(text: String) {
        if (!accessibilityManager.isEnabled) return
        val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
        event.className = TtsSpeaker::class.java.name
        event.packageName = appContext.packageName
        event.text.add(text)
        runCatching { accessibilityManager.sendAccessibilityEvent(event) }
    }

    /**
     * Mówi komunikat, a jeśli silnik jest martwy lub wypowiedź cicho się nie
     * powiedzie — odbudowuje TextToSpeech i ponawia raz. Dzięki temu głos wraca
     * sam, nawet gdy usługa TTS padła po godzinach.
     */
    private suspend fun speakWithRecovery(text: String, settings: AppSettings) {
        if (speakOnce(text, settings)) return
        if (reinitialize()) {
            speakOnce(text, settings)
        }
    }

    /**
     * @return true jeśli wypowiedź wystartowała i dobiegła końca poprawnie;
     *         false jeśli silnik był niedostępny lub zgłosił błąd (sygnał do
     *         odbudowy silnika).
     */
    private suspend fun speakOnce(text: String, settings: AppSettings): Boolean {
        val engine = tts ?: return false
        if (!isReady) return false

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

        val outcome = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (continuation.isActive) continuation.resume(false)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                })

                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
                }
                val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                if (result != TextToSpeech.SUCCESS && continuation.isActive) {
                    continuation.resume(false)
                }
                continuation.invokeOnCancellation { runCatching { engine.stop() } }
            }
        }

        // Przekroczenie czasu (null) też traktujemy jako porażkę wartą odbudowy —
        // zawieszony silnik nie odezwie się sam.
        return outcome == true
    }

    /**
     * Odbudowuje silnik TextToSpeech od zera i czeka (z limitem czasu) aż zgłosi
     * gotowość. Wołane gdy wypowiedź zawiodła — najczęstsza przyczyna „ciszy po
     * godzinach” na Androidzie.
     */
    private suspend fun reinitialize(): Boolean {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        isReady = false

        return withTimeoutOrNull(REINIT_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                lateinit var engine: TextToSpeech
                engine = TextToSpeech(appContext) { status ->
                    val ok = status == TextToSpeech.SUCCESS
                    isReady = ok
                    if (ok) {
                        runCatching { engine.language = Locale("pl", "PL") }
                        SettingsStore.applyAvailableVoices(engine)
                    }
                    if (continuation.isActive) continuation.resume(ok)
                }
                tts = engine
                continuation.invokeOnCancellation { runCatching { engine.shutdown() } }
            }
        } ?: false
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

    private companion object {
        const val REINIT_TIMEOUT_MS = 5_000L
    }
}
