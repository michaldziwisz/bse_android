package eu.blueseaeye.bse.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostyka przyczyny zakończenia aplikacji. Instaluje globalny handler
 * nieprzechwyconych wyjątków, zapisuje pełny stacktrace do pliku, a przy
 * następnym uruchomieniu udostępnia go (do pokazania i skopiowania do schowka).
 * Zamiast zgadywać, dlaczego aplikacja „wywala się” po godzinach, mamy twardy
 * dowód prosto z urządzenia Michała. Na Androidzie stacktrace zawiera czytelne
 * nazwy klas i metod od razu — bez symbolikacji jak na iOS.
 */
object CrashReporter {
    private const val FILE_NAME = "bse-last-crash.txt"

    @Volatile
    private var appContext: Context? = null

    /** Wołać RAZ, jak najwcześniej (Application.onCreate). */
    fun install(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(ctx, format(thread, throwable)) }
            // Oddaj sterowanie domyślnemu handlerowi, by nie maskować crasha.
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Odczytuje i USUWA zapisany opis ostatniego crasha (jeśli był). */
    fun consumeLastCrashReason(context: Context): String? {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull()
        runCatching { file.delete() }
        return text?.takeIf { it.isNotBlank() }
    }

    private fun format(thread: Thread, throwable: Throwable): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val sb = StringBuilder()
        sb.append("[").append(ts).append("] Wątek: ").append(thread.name).append('\n')
        var cause: Throwable? = throwable
        var depth = 0
        while (cause != null && depth < 5) {
            sb.append(if (depth == 0) "Wyjątek: " else "Przyczyna: ")
            sb.append(cause.javaClass.name).append(": ").append(cause.message ?: "brak opisu").append('\n')
            cause.stackTrace.take(12).forEach { sb.append("  at ").append(it.toString()).append('\n') }
            cause = cause.cause
            depth++
        }
        return sb.toString()
    }

    private fun write(context: Context, text: String) {
        val file = File(context.filesDir, FILE_NAME)
        runCatching { file.writeText(text) }
    }
}
