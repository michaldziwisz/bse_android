package eu.blueseaeye.bse

import eu.blueseaeye.bse.model.AdminFailureMessages
import eu.blueseaeye.bse.model.AdministrationAction
import eu.blueseaeye.bse.network.ApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Komunikaty o niepowodzeniu czynności urządzenia (kalibracja, restart).
 *
 * Sens tych testów: przed poprawką użytkownik dostawał surowe „Błąd serwera 404",
 * które nic nie mówiło o przyczynie — a 404 na kalibracji jest stanem NORMALNYM
 * na starszym firmware i w trybie demonstracyjnym. Każdy przypadek pozytywny ma
 * tu parę negatywną, żeby „zielono" nie mogło znaczyć „sprawdzam cokolwiek".
 */
class AdminFailureMessagesTest {

    private fun http(code: Int) = ApiException.HttpStatus(code, null)

    @Test
    fun `404 w trybie demo wskazuje na serwer pokazowy i podaje wyjscie`() {
        val wynik = AdminFailureMessages.describe(
            AdministrationAction.CALIBRATE, http(404), demoMode = true
        )
        assertTrue(wynik.contains("demonstracyjnym"))
        assertTrue(wynik.contains("Wyłącz tryb demonstracyjny"))
        // para negatywna: NIE zrzucamy winy na firmware, gdy przyczyną jest tryb demo
        assertFalse(wynik.contains("starsze oprogramowanie"))
        // para negatywna: surowy kod HTTP nie trafia do użytkownika
        assertFalse(wynik.contains("404"))
    }

    @Test
    fun `404 poza trybem demo wskazuje na starsze oprogramowanie urzadzenia`() {
        val wynik = AdminFailureMessages.describe(
            AdministrationAction.CALIBRATE, http(404), demoMode = false
        )
        assertTrue(wynik.contains("starsze oprogramowanie"))
        // para negatywna: nie mówimy o trybie demo, gdy jest wyłączony
        assertFalse(wynik.contains("demonstracyjnym"))
        assertFalse(wynik.contains("404"))
    }

    @Test
    fun `przekroczenie czasu radzi sprawdzic siec urzadzenia`() {
        val wynik = AdminFailureMessages.describe(
            AdministrationAction.REBOOT, ApiException.Timeout, demoMode = false
        )
        assertTrue(wynik.contains("nie odpowiedziało w czasie"))
        assertTrue(wynik.contains("BlueSeaEye"))
        // para negatywna: timeout to NIE brak obsługi czynności
        assertFalse(wynik.contains("nie jest obsługiwana"))
    }

    @Test
    fun `inny blad HTTP nie udaje braku obslugi ani trybu demo`() {
        val wynik = AdminFailureMessages.describe(
            AdministrationAction.CALIBRATE, http(500), demoMode = false
        )
        // 500 to awaria urządzenia, a nie brak funkcji — komunikat musi je różnicować
        assertFalse(wynik.contains("nie jest obsługiwana"))
        assertFalse(wynik.contains("demonstracyjnym"))
        assertTrue(wynik.contains("nie udała się"))
        assertTrue(wynik.contains("500"))
    }

    @Test
    fun `blad bez komunikatu nie daje null w tekscie dla uzytkownika`() {
        val wynik = AdminFailureMessages.describe(
            AdministrationAction.REBOOT, IOException(), demoMode = false
        )
        assertFalse(wynik.contains("null"))
        assertTrue(wynik.contains("Nieznany błąd"))
    }

    @Test
    fun `nazwa czynnosci zgadza sie z akcja`() {
        assertTrue(
            AdminFailureMessages.describe(AdministrationAction.CALIBRATE, http(404), false)
                .startsWith("Kalibracja")
        )
        assertTrue(
            AdminFailureMessages.describe(AdministrationAction.REBOOT, http(404), false)
                .startsWith("Restart urządzenia")
        )
        // para negatywna: komunikat restartu nie mówi o kalibracji i odwrotnie
        assertFalse(
            AdminFailureMessages.describe(AdministrationAction.REBOOT, http(404), false)
                .contains("Kalibracja")
        )
    }

    @Test
    fun `komunikat jest zdaniem po polsku z diakrytykami, nie kodem bledu`() {
        val wynik = AdminFailureMessages.describe(
            AdministrationAction.CALIBRATE, http(404), demoMode = false
        )
        // czytnik ekranu ma przeczytać zdanie, nie surowy komunikat techniczny
        assertTrue(wynik.endsWith("."))
        assertTrue(wynik.any { it in "ąćęłńóśźż" })
        assertEquals(wynik.trim(), wynik)
    }
}
