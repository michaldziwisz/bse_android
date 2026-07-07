package eu.blueseaeye.bse.data

import android.content.Context
import android.speech.tts.TextToSpeech
import eu.blueseaeye.bse.model.AppSettings
import eu.blueseaeye.bse.model.VoiceOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * Trwałe przechowywanie ustawień w SharedPreferences jako jeden dokument JSON.
 * Tolerancyjne dekodowanie (ignoreUnknownKeys + wartości domyślne data class)
 * odpowiada tolerancyjnemu Codable w wersji iOS: brak klucza lub zły typ nie
 * unieważnia całego zapisu.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val _settings = MutableStateFlow(loadInitial())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun update(mutation: (AppSettings) -> AppSettings) {
        val next = mutation(_settings.value).clamped()
        if (next != _settings.value) {
            _settings.value = next
            persist(next)
        }
    }

    private fun persist(settings: AppSettings) {
        runCatching {
            prefs.edit().putString(KEY_SETTINGS, json.encodeToString(settings)).apply()
        }
    }

    private fun loadInitial(): AppSettings {
        val stored = prefs.getString(KEY_SETTINGS, null)
        val decoded = stored?.let {
            runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull()
        }
        val base = (decoded ?: AppSettings()).clamped()
        return if (base.readingVoiceIdentifier == null) {
            base.copy(readingVoiceIdentifier = defaultPolishVoice)
        } else {
            base
        }
    }

    companion object {
        private const val PREFS_NAME = "bse.settings"
        private const val KEY_SETTINGS = "settings"

        /**
         * Wypełniane raz przez [applyAvailableVoices] gdy syntezator jest gotowy.
         * Do czasu inicjalizacji pozostaje null (użyty zostanie głos domyślny).
         */
        @Volatile
        private var availableVoices: List<VoiceOption> = emptyList()

        @Volatile
        private var defaultPolishVoice: String? = null

        fun applyAvailableVoices(tts: TextToSpeech) {
            val voices = runCatching {
                tts.voices?.sortedWith(
                    compareBy({ it.locale.toLanguageTag() }, { it.name })
                )?.map { voice ->
                    VoiceOption(
                        id = voice.name,
                        name = voice.name,
                        language = voice.locale.displayName.ifBlank { voice.locale.toLanguageTag() }
                    )
                }
            }.getOrNull().orEmpty()
            availableVoices = voices

            defaultPolishVoice = runCatching {
                tts.voices?.firstOrNull { it.locale.language == Locale("pl").language }?.name
            }.getOrNull()
        }

        fun voices(): List<VoiceOption> = availableVoices
    }
}
