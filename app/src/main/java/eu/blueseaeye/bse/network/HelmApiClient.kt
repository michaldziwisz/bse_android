package eu.blueseaeye.bse.network

import eu.blueseaeye.bse.model.AdministrationAction
import eu.blueseaeye.bse.model.AppSettings
import eu.blueseaeye.bse.model.HelmReadings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Wyjątki API urządzenia z komunikatami dla użytkownika. */
sealed class ApiException(message: String) : Exception(message) {
    object InvalidResponse : ApiException("Nieprawidłowa odpowiedź serwera.")
    object Timeout : ApiException("Przekroczono czas oczekiwania na odpowiedź steru.")
    class HttpStatus(val code: Int, val bodyText: String?) : ApiException(
        if (!bodyText.isNullOrEmpty()) "Błąd serwera $code: $bodyText" else "Błąd serwera $code."
    )
}

/**
 * Klient HTTP urządzenia BlueSeaEye oparty o HttpURLConnection (zero zależności).
 *
 * Kontrakt (odtworzony z wbudowanego frontendu urządzenia):
 * GET {base}/helm?time=<epoch_ms>&source=<klucz>&window=<averageWindow*1000 ms>.
 * Parametr czasu to „time" (NIE „t"), a „window" to okno uśredniania w
 * MILISEKUNDACH — surowa wartość 1–5 daje na sprzęcie HTTP 400.
 */
class HelmApiClient(
    private val requestTimeoutMs: Int = 4000
) {
    suspend fun fetchHelmReadings(settings: AppSettings): HelmReadings = withContext(Dispatchers.IO) {
        val base = settings.deviceBaseUrl().trimEnd('/')
        val time = System.currentTimeMillis()
        val window = settings.averageWindow * 1000
        val urlString = "$base/helm?time=$time&source=${settings.courseSource.key}&window=$window"
        val (code, body) = performGet(urlString)
        if (code !in 200..299) {
            throw ApiException.HttpStatus(code, body)
        }
        parseReadings(body)
    }

    suspend fun performAdministrationAction(
        action: AdministrationAction,
        settings: AppSettings
    ): Unit = withContext(Dispatchers.IO) {
        val base = settings.deviceBaseUrl().trimEnd('/')
        val urlString = "$base/${action.path}"
        val (code, body) = performGet(urlString)
        if (code !in 200..299) {
            throw ApiException.HttpStatus(code, body)
        }
    }

    private fun performGet(urlString: String): Pair<Int, String?> {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = requestTimeoutMs
                readTimeout = requestTimeoutMs
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }
            return code to body
        } catch (e: java.net.SocketTimeoutException) {
            throw ApiException.Timeout
        } catch (e: IOException) {
            throw ApiException.InvalidResponse
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseReadings(body: String?): HelmReadings {
        if (body.isNullOrBlank()) throw ApiException.InvalidResponse
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            throw ApiException.InvalidResponse
        }
        fun doubleOrNull(key: String): Double? {
            if (!json.has(key) || json.isNull(key)) return null
            return when (val value = json.opt(key)) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
        }
        return HelmReadings(
            cgfa = doubleOrNull("cgfa"),
            cgf = doubleOrNull("cgf"),
            coga = doubleOrNull("coga"),
            cog = doubleOrNull("cog"),
            hdga = doubleOrNull("hdga"),
            hdg = doubleOrNull("hdg"),
            rsa = doubleOrNull("rsa"),
            wa = doubleOrNull("wa")
        )
    }
}
