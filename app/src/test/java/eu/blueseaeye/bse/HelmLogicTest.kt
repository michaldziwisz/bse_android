package eu.blueseaeye.bse

import eu.blueseaeye.bse.model.AppSettings
import eu.blueseaeye.bse.model.CourseSource
import eu.blueseaeye.bse.model.HelmMath
import eu.blueseaeye.bse.model.HelmSnapshot
import eu.blueseaeye.bse.model.TargetMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class HelmLogicTest {

    @Test
    fun relativeCourseWrapsAcrossNorth() {
        assertEquals(-20.0, HelmMath.relativeCourse(350.0, 10.0), 0.0001)
        assertEquals(20.0, HelmMath.relativeCourse(10.0, 350.0), 0.0001)
        assertEquals(0.0, HelmMath.relativeCourse(100.0, 100.0), 0.0001)
    }

    @Test
    fun normalizedCourseStaysInRange() {
        assertEquals(10.0, HelmMath.normalizedCourse(370.0), 0.0001)
        assertEquals(350.0, HelmMath.normalizedCourse(-10.0), 0.0001)
    }

    @Test
    fun deviceBaseUrlFromPlainHost() {
        val s = AppSettings(deviceHost = "192.168.4.1")
        assertEquals("http://192.168.4.1/api", s.deviceBaseUrl())
    }

    @Test
    fun deviceBaseUrlFromFullUrlWithoutApi() {
        val s = AppSettings(deviceHost = "http://10.0.0.5")
        assertEquals("http://10.0.0.5/api", s.deviceBaseUrl())
    }

    @Test
    fun deviceBaseUrlEmptyFallsBackToDefault() {
        val s = AppSettings(deviceHost = "   ")
        assertEquals("http://192.168.4.1/api", s.deviceBaseUrl())
    }

    @Test
    fun clampCoercesOutOfRangeValues() {
        val s = AppSettings(averageWindow = 99, readingRate = 5.0, errorRange = 200.0).clamped()
        assertEquals(5, s.averageWindow)
        assertEquals(50.0, s.readingRate, 0.0001)
        assertEquals(60.0, s.errorRange, 0.0001)
    }

    @Test
    fun displayedValueUsesCourseWhenNoTarget() {
        val settings = AppSettings(target = TargetMode.NONE)
        val snapshot = HelmSnapshot(course = 123.4, rudder = null, wind = null, fetchedAt = Date())
        assertEquals(123, snapshot.displayedValue(settings))
    }

    @Test
    fun displayedValueUsesDeviationWithTarget() {
        val settings = AppSettings(target = TargetMode.COURSE, targetCourse = 100.0)
        val snapshot = HelmSnapshot(course = 110.0, rudder = null, wind = null, fetchedAt = Date())
        assertEquals(10, snapshot.displayedValue(settings))
    }

    @Test
    fun spokenReadingMatchesDeviceFormat() {
        // 1:1 z urządzeniem: sama liczba kursu + ster jako „Lewo N" bez słowa
        // „Ster", bez czytania wiatru w trybie kurs.
        val settings = AppSettings(target = TargetMode.NONE)
        val snapshot = HelmSnapshot(course = 90.0, rudder = -15.0, wind = 45.0, fetchedAt = Date())
        assertEquals("90, Lewo 15", snapshot.spokenReading(settings))
    }

    @Test
    fun spokenReadingRudderRightAndDeviation() {
        // Tryb „Zadany kurs": odchyłka dodatnia (+10, sygnał z lewej) => „prawiej 10".
        val settings = AppSettings(target = TargetMode.COURSE, targetCourse = 100.0)
        val snapshot = HelmSnapshot(course = 110.0, rudder = 5.0, wind = null, fetchedAt = Date())
        assertEquals("prawiej 10, Prawo 5", snapshot.spokenReading(settings))
    }

    @Test
    fun spokenReadingNegativeDeviationSaysPrawiej() {
        // Odchyłka ujemna (-3, sygnał z prawej) => „lewiej 3"; ster -2 => „Lewo 2".
        val settings = AppSettings(target = TargetMode.COURSE, targetCourse = 100.0)
        val snapshot = HelmSnapshot(course = 97.0, rudder = -2.0, wind = null, fetchedAt = Date())
        assertEquals("lewiej 3, Lewo 2", snapshot.spokenReading(settings))
    }

    @Test
    fun spokenReadingSkipsRudderWhenDisabled() {
        // Gdy użytkownik wyłączy odczyt wychylenia steru, wypowiedź zawiera
        // samą wartość główną — bez części „Prawo/Lewo N".
        val settings = AppSettings(target = TargetMode.NONE, announceRudderAngle = false)
        val snapshot = HelmSnapshot(course = 90.0, rudder = -15.0, wind = null, fetchedAt = Date())
        assertEquals("90", snapshot.spokenReading(settings))
    }

    @Test
    fun spokenReadingCourseUnknownWhenNoValue() {
        val settings = AppSettings(target = TargetMode.NONE)
        val snapshot = HelmSnapshot(course = null, rudder = null, wind = null, fetchedAt = Date())
        assertEquals("Kurs nieznany", snapshot.spokenReading(settings))
    }

    @Test
    fun spokenReadingEmptyInWindModeWithoutWind() {
        // Tryb wiatru bez danych o wietrze: nic nie jest wypowiadane (także ster).
        val settings = AppSettings(target = TargetMode.WIND, targetWind = 30.0)
        val snapshot = HelmSnapshot(course = 90.0, rudder = -15.0, wind = null, fetchedAt = Date())
        assertEquals("", snapshot.spokenReading(settings))
    }

    @Test
    fun windTargetModeUsesWindDeviation() {
        val settings = AppSettings(target = TargetMode.WIND, targetWind = 40.0)
        val snapshot = HelmSnapshot(course = 200.0, rudder = null, wind = 50.0, fetchedAt = Date())
        assertEquals(10, snapshot.displayedValue(settings))
    }

    @Test
    fun demoModeUsesDemoServer() {
        val settings = AppSettings(demoMode = true, deviceHost = "192.168.4.1")
        assertEquals(AppSettings.DEMO_BASE_URL, settings.deviceBaseUrl())
    }

    @Test
    fun courseSourceFromKeyDefaults() {
        assertEquals(CourseSource.CGFA, CourseSource.fromKey("nieznane"))
        assertEquals(CourseSource.HDG, CourseSource.fromKey("hdg"))
    }

    @Test
    fun snapshotWithoutCourseHasNullDisplay() {
        val settings = AppSettings(target = TargetMode.NONE)
        val snapshot = HelmSnapshot(course = null, rudder = null, wind = null, fetchedAt = Date())
        assertNull(snapshot.displayedValue(settings))
    }
}
