package eu.blueseaeye.bse.model

import eu.blueseaeye.bse.network.ApiException

/**
 * Zamiana błędu czynności urządzenia (kalibracja, restart) na zdanie zrozumiałe
 * dla użytkownika.
 *
 * Wydzielone z [eu.blueseaeye.bse.monitor.HelmMonitor] jako czysta funkcja, żeby
 * dało się to sprawdzić testem JVM — monitor wymaga kontekstu Androida.
 *
 * Najważniejszy przypadek to 404. Endpoint `/api/calibrate` istnieje TYLKO
 * w nowszym firmware urządzenia (build z 11.07.2026); starsze go nie ma,
 * a serwer demonstracyjny nie obsługuje ani kalibracji, ani restartu. Surowe
 * „Błąd serwera 404" nie mówi użytkownikowi nic o przyczynie.
 */
object AdminFailureMessages {

    fun describe(
        action: AdministrationAction,
        error: Throwable,
        demoMode: Boolean
    ): String {
        val nazwa = when (action) {
            AdministrationAction.CALIBRATE -> "Kalibracja"
            AdministrationAction.REBOOT -> "Restart urządzenia"
        }
        val status = error as? ApiException.HttpStatus
        return when {
            status?.code == 404 && demoMode ->
                "$nazwa nie działa w trybie demonstracyjnym — serwer pokazowy obsługuje tylko odczyt. Wyłącz tryb demonstracyjny i połącz się z urządzeniem."
            status?.code == 404 ->
                "$nazwa nie jest obsługiwana przez to urządzenie. Najpewniej ma starsze oprogramowanie, w którym ta czynność nie istnieje."
            error is ApiException.Timeout ->
                "$nazwa nie doszła: urządzenie nie odpowiedziało w czasie. Sprawdź połączenie z siecią BlueSeaEye."
            else -> "$nazwa nie udała się. ${error.message ?: "Nieznany błąd."}"
        }
    }
}
