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
    }

    fun targetValue(settings: AppSettings): Double? = when (settings.target) {
        TargetMode.NONE -> null
        TargetMode.COURSE -> settings.targetCourse
    }

    fun accessibilitySummary(settings: AppSettings): String {
        val headingText = run {
            val value = displayedValue(settings)
            if (value != null) {
                if (settings.target == TargetMode.COURSE) "Odchyłka od kursu $value stopni"
                else "Kurs $value stopni"
            } else {
                if (settings.target == TargetMode.COURSE) "Odchyłka od kursu nieznana"
                else "Kurs nieznany"
            }
        }
        val parts = mutableListOf(headingText)
        rudder?.let {
            val side = if (it >= 0) "prawo" else "lewo"
            parts.add("Ster ${abs(it.roundToInt())} stopni $side")
        }
        wind?.let {
            parts.add("Wiatr ${it.roundToInt()} stopni")
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
