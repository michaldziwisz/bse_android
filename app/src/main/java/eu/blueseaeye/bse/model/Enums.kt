package eu.blueseaeye.bse.model

/** Źródło kursu — klucze pól jak w odpowiedzi urządzenia BlueSeaEye. */
enum class CourseSource(val key: String, val title: String) {
    CGFA("cgfa", "Uśredniony kurs filtrowany"),
    COGA("coga", "Uśredniony kurs nad ziemią"),
    HDGA("hdga", "Uśredniony kurs kompasowy"),
    CGF("cgf", "Kurs filtrowany"),
    COG("cog", "Kurs nad ziemią"),
    HDG("hdg", "Kurs kompasowy");

    companion object {
        fun fromKey(key: String?): CourseSource =
            entries.firstOrNull { it.key == key } ?: CGFA
    }
}

/** Sposób ogłaszania odczytów. */
enum class ReadingOutputMode(val key: String, val title: String) {
    TTS("tts", "Synteza mowy"),
    ARIA("aria", "Czytnik ekranu");

    companion object {
        fun fromKey(key: String?): ReadingOutputMode =
            entries.firstOrNull { it.key == key } ?: ARIA
    }
}

/** Kształt fali generowanego tonu. */
enum class ToneWaveform(val key: String, val title: String) {
    SINE("sine", "Sinusoidalny"),
    TRIANGLE("triangle", "Trójkątny"),
    SAWTOOTH("sawtooth", "Piłokształtny"),
    SQUARE("square", "Prostokątny");

    companion object {
        fun fromKey(key: String?): ToneWaveform =
            entries.firstOrNull { it.key == key } ?: TRIANGLE
    }
}

/** Tryb odczytu: pełny kurs albo odchyłka od zadanego kursu. */
enum class TargetMode(val key: String, val title: String) {
    NONE("none", "Kurs"),
    COURSE("course", "Odchyłka od zadanego kursu");

    companion object {
        fun fromKey(key: String?): TargetMode =
            entries.firstOrNull { it.key == key } ?: NONE
    }
}

/** Akcja administracyjna urządzenia. */
enum class AdministrationAction(val path: String) {
    CALIBRATE("calibrate"),
    REBOOT("reboot")
}

/**
 * Zachowanie odczytu po ponownym uruchomieniu aplikacji (gdy agresywny system —
 * np. Samsung One UI, Xiaomi HyperOS — ubił proces w tle, a foreground service
 * został wskrzeszony przez system). Domyślnie aplikacja NIC nie wznawia sama —
 * czeka na świadome włączenie odczytu przyciskiem.
 */
enum class AutoResumeMode(val key: String, val title: String) {
    NEVER("never", "Nie wznawiaj (domyślnie)"),
    BACKGROUND_ONLY("background", "Tylko po wznowieniu w tle"),
    ALWAYS("always", "Zawsze przy starcie");

    companion object {
        fun fromKey(key: String?): AutoResumeMode =
            entries.firstOrNull { it.key == key } ?: NEVER
    }
}

/** Opcja głosu syntezatora. */
data class VoiceOption(
    val id: String,
    val name: String,
    val language: String
) {
    val title: String get() = "$name ($language)"
}
