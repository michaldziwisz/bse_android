package eu.blueseaeye.bse.model

import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

/** Surowe odczyty z urządzenia (pola opcjonalne — urządzenie filtruje po `source`). */
data class HelmReadings(
    val cgfa: Double? = null,
    val cgf: Double? = null,
    val coga: Double? = null,
    val cog: Double? = null,
    val hdga: Double? = null,
    val hdg: Double? = null,
    val rsa: Double? = null,
    val wa: Double? = null
) {
    fun course(source: CourseSource): Double? = when (source) {
        CourseSource.CGFA -> cgfa
        CourseSource.COGA -> coga
        CourseSource.HDGA -> hdga
        CourseSource.CGF -> cgf
        CourseSource.COG -> cog
        CourseSource.HDG -> hdg
    }
}

/** Przetworzony odczyt gotowy do prezentacji i ogłoszeń. */
data class HelmSnapshot(
    val course: Double?,
    val rudder: Double?,
    val wind: Double?,
    val fetchedAt: Date
) {
    fun displayedValue(settings: AppSettings): Int? {
        val current = currentValue(settings) ?: return null
        val target = targetValue(settings)
        return if (target != null) {
            HelmMath.relativeCourse(current, target).roundToInt()
        } else {
            current.roundToInt()
        }
    }

    fun currentValue(settings: AppSettings): Double? = when (settings.target) {
        TargetMode.NONE, TargetMode.COURSE -> course
        TargetMode.WIND -> wind
    }

    fun targetValue(settings: AppSettings): Double? = when (settings.target) {
        TargetMode.NONE -> null
        TargetMode.COURSE -> settings.targetCourse
        TargetMode.WIND -> settings.targetWind
    }

    /**
     * Komunikat odczytu składany DOKŁADNIE tak, jak wbudowany frontend
     * urządzenia BlueSeaEye (main.js): sama liczba głównej wartości (kurs,
     * odchyłka od kursu albo odchyłka od kąta do wiatru) bez etykiety słownej,
     * a następnie ster jako „Prawo N" / „Lewo N" (bez słowa „Ster"). Elementy
     * łączone przecinkiem. Zwraca pusty ciąg, gdy nie ma nic do powiedzenia
     * (tryb wiatru bez danych o wietrze) — wywołujący pomija wtedy wypowiedź.
     *
     * Zgodność 1:1 z urządzeniem: przy braku wartości głównej w trybie kursu
     * i odchyłki od kursu urządzenie mówi „Kurs nieznany" (nie ma osobnego
     * komunikatu dla odchyłki). W trybie wiatru bez danych — na życzenie
     * użytkownika NIC nie jest wypowiadane (cała wypowiedź pominięta, także ster).
     */
    fun spokenReading(settings: AppSettings): String {
        val parts = mutableListOf<String>()
        val value = displayedValue(settings)
        if (value != null) {
            parts.add("$value")
        } else {
            when (settings.target) {
                TargetMode.NONE, TargetMode.COURSE -> parts.add("Kurs nieznany")
                TargetMode.WIND -> return ""
            }
        }
        rudder?.let {
            val side = if (it > 0) "Prawo" else "Lewo"
            parts.add("$side ${abs(it.roundToInt())}")
        }
        return parts.joinToString(", ")
    }
}

object HelmMath {
    fun relativeCourse(course: Double, targetCourse: Double): Double {
        var delta = course - targetCourse
        while (delta <= -180) delta += 360
        while (delta > 180) delta -= 360
        return delta
    }

    fun normalizedCourse(course: Double): Double {
        val rounded = course.roundToInt().toDouble()
        return ((rounded % 360) + 360) % 360
    }
}
