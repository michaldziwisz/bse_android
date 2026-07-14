package eu.blueseaeye.bse.model

import kotlinx.serialization.Serializable
import java.net.URI

/**
 * Ustawienia aplikacji. Serializowane przez kotlinx.serialization z tolerancją
 * na brakujące/nieznane klucze (patrz SettingsStore) — odpowiednik tolerancyjnego
 * dekodowania Codable w wersji iOS.
 */
@Serializable
data class AppSettings(
    val averageWindow: Int = 3,
    val avoidSignalsOverlap: Boolean = false,
    val courseSource: CourseSource = CourseSource.CGFA,
    val deviceHost: String = DEFAULT_DEVICE_HOST,
    val readingDelay: Double = 3.0,
    val readingInterval: Double = 5.0,
    val readingOutput: ReadingOutputMode = ReadingOutputMode.ARIA,
    val readingRate: Double = 150.0,
    val readingVoiceIdentifier: String? = null,
    val readingVolume: Double = 100.0,
    val soundSignalsEnabled: Boolean = true,
    val toneDelay: Double = 1.0,
    val referenceTone: Boolean = true,
    val toneBaseOffset: Double = 2.0,
    val toneOnCourse: Boolean = true,
    val toneType: ToneWaveform = ToneWaveform.TRIANGLE,
    val toneVolume: Double = 25.0,
    val shortTones: Boolean = true,
    val broadTonalSpread: Boolean = false,
    val target: TargetMode = TargetMode.NONE,
    val targetCourse: Double? = null,
    val targetWind: Double? = null,
    val errorThreshold: Double = 1.0,
    val errorRange: Double = 30.0,
    val announceRudderAngle: Boolean = true,
    val invertRudderAngle: Boolean = false,
    val rudderAngleCorrection: Double = 0.0,
    val autoResumeMode: AutoResumeMode = AutoResumeMode.NEVER,
    val demoMode: Boolean = false,
    val keepDeviceWifi: Boolean = true
) {
    /**
     * Adres bazowy API urządzenia zbudowany z [deviceHost]. Akceptuje samo IP
     * lub nazwę hosta, a także pełny URL wpisany przez użytkownika.
     *
     * W trybie demonstracyjnym ([demoMode]) zwraca zawsze serwer demonstracyjny
     * w internecie zamiast adresu sprzętu w sieci lokalnej — dzięki temu odczyt
     * i akcje administracyjne działają bez łodzi i bez fizycznego urządzenia.
     */
    fun deviceBaseUrl(): String {
        if (demoMode) {
            return DEMO_BASE_URL
        }
        val host = deviceHost.trim().ifEmpty { DEFAULT_DEVICE_HOST }
        val hasScheme = host.startsWith("http://", ignoreCase = true) ||
            host.startsWith("https://", ignoreCase = true)
        if (hasScheme) {
            return try {
                val uri = URI(host)
                if (uri.path != null && uri.path.contains("api")) {
                    host.trimEnd('/')
                } else {
                    host.trimEnd('/') + "/api"
                }
            } catch (_: Exception) {
                FALLBACK_DEVICE_BASE_URL
            }
        }
        return "http://$host/api"
    }

    /** Zwraca kopię z wartościami dociśniętymi do dozwolonych zakresów. */
    fun clamped(): AppSettings {
        val normalizedTarget = targetCourse?.let {
            val rounded = Math.round(it).toDouble()
            ((rounded % 360) + 360) % 360
        }
        val normalizedTargetWind = targetWind?.let {
            val rounded = Math.round(it).toDouble()
            ((rounded % 360) + 360) % 360
        }
        return copy(
            averageWindow = averageWindow.coerceIn(1, 5),
            readingDelay = readingDelay.coerceIn(0.0, 30.0),
            readingInterval = readingInterval.coerceIn(1.0, 45.0),
            readingRate = readingRate.coerceIn(50.0, 400.0),
            readingVolume = readingVolume.coerceIn(0.0, 100.0),
            toneDelay = toneDelay.coerceIn(0.5, 5.0),
            toneBaseOffset = toneBaseOffset.coerceIn(0.0, 6.0),
            toneVolume = toneVolume.coerceIn(0.0, 100.0),
            errorThreshold = errorThreshold.coerceIn(1.0, 15.0),
            errorRange = errorRange.coerceIn(15.0, 60.0),
            rudderAngleCorrection = rudderAngleCorrection.coerceIn(-90.0, 90.0),
            targetCourse = normalizedTarget,
            targetWind = normalizedTargetWind
        )
    }

    companion object {
        /** Domyślny host urządzenia BlueSeaEye w trybie access pointa (brama SoftAP). */
        const val DEFAULT_DEVICE_HOST = "192.168.4.1"
        const val FALLBACK_DEVICE_BASE_URL = "http://192.168.4.1/api"

        /**
         * Adres bazowy serwera demonstracyjnego BlueSeaEye. W trybie demo
         * aplikacja łączy się z nim przez internet zamiast ze sprzętem w sieci
         * lokalnej — pozwala testować bez łodzi i bez fizycznego urządzenia.
         */
        const val DEMO_BASE_URL = "https://blueseaeye.eu/api"

        /** SSID i hasło access pointa urządzenia BlueSeaEye (tryb SoftAP). */
        const val DEVICE_WIFI_SSID = "BlueSeaEye"
        const val DEVICE_WIFI_PASSPHRASE = "blueseaeye"

        val DEFAULT = AppSettings()
    }
}
