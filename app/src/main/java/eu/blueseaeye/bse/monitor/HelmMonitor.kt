package eu.blueseaeye.bse.monitor

import eu.blueseaeye.bse.audio.TonePlayer
import eu.blueseaeye.bse.audio.TtsSpeaker
import eu.blueseaeye.bse.data.SettingsStore
import eu.blueseaeye.bse.model.AdministrationAction
import eu.blueseaeye.bse.model.AppSettings
import eu.blueseaeye.bse.model.AutoResumeMode
import eu.blueseaeye.bse.model.HelmMath
import eu.blueseaeye.bse.model.HelmSnapshot
import eu.blueseaeye.bse.model.TargetMode
import eu.blueseaeye.bse.network.ApiException
import eu.blueseaeye.bse.network.HelmApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** Stan prezentowany przez monitora w UI. */
data class MonitorState(
    val snapshot: HelmSnapshot? = null,
    val isReadingEnabled: Boolean = false,
    val isPolling: Boolean = false,
    val isConnectionLost: Boolean = false,
    val lastAnnouncement: String = "",
    val errorMessage: String? = null,
    val adminMessage: String? = null,
    val isBusy: Boolean = false,
    val lastCrashReason: String? = null
)

/**
 * Pętla pollingu urządzenia z odczytem głosowym, tonami odchyłki i alertami
 * utraty połączenia. Wierny port HelmMonitor z wersji iOS.
 */
class HelmMonitor(
    private val settingsStore: SettingsStore,
    private val apiClient: HelmApiClient,
    private val tonePlayer: TonePlayer,
    private val speaker: TtsSpeaker,
    private val onConnectionLostAlert: suspend (String) -> Unit = {},
    private val onConnectionRecovered: () -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    private val statusIntervalMs = 500L
    private val loopDelayMs = 100L
    private val frequencyMid = 440.0
    private val connectionAlertRepeatIntervalMs = 20_000L

    private var loopJob: Job? = null
    private var readingJob: Job? = null
    private var signalJob: Job? = null

    private var isReadingInProgress = false
    private var isSignalInProgress = false
    private var isSpeechActive = false

    private var lastReadAt = 0L
    private var lastSignalAt = 0L
    private var lastFetchAt = 0L
    private var lastConnectionAlertAt = 0L
    private var lastSignaledSnapshot: HelmSnapshot? = null
    private var speechGeneration = 0L

    private val settings: AppSettings get() = settingsStore.current

    fun start() {
        if (loopJob != null) return
        _state.value = _state.value.copy(isPolling = true)
        loopJob = scope.launch { runLoop() }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        readingJob?.cancel()
        signalJob?.cancel()
        speechGeneration++
        isSpeechActive = false
        speaker.stop()
        tonePlayer.stop()
        settingsStore.readingActive = false
        _state.value = _state.value.copy(isPolling = false, isReadingEnabled = false)
    }

    fun toggleReading() {
        val enabled = !_state.value.isReadingEnabled
        _state.value = _state.value.copy(isReadingEnabled = enabled, errorMessage = null)
        settingsStore.readingActive = enabled
        if (!enabled) {
            speechGeneration++
            isSpeechActive = false
            speaker.stop()
            tonePlayer.stop()
        }
    }

    /**
     * Wznawia odczyt po ponownym starcie procesu, jeśli był włączony w chwili
     * ubicia i użytkownik na to pozwolił w ustawieniach. [launchedFromService]
     * mówi, czy proces wstał z wskrzeszenia foreground service (bez interakcji
     * użytkownika) — decyduje o trybie BACKGROUND_ONLY.
     */
    fun resumeIfNeeded(launchedFromService: Boolean) {
        if (_state.value.isReadingEnabled) return
        if (!settingsStore.readingActive) return

        when (settingsStore.current.autoResumeMode) {
            AutoResumeMode.NEVER -> return
            AutoResumeMode.BACKGROUND_ONLY -> if (!launchedFromService) return
            AutoResumeMode.ALWAYS -> Unit
        }

        _state.value = _state.value.copy(isReadingEnabled = true)
    }

    /**
     * Jeśli poprzednie uruchomienie zakończyło się crashem, udostępnia jego
     * pełny opis (do pokazania i skopiowania do schowka) — twardy dowód
     * przyczyny zamiast zgadywania. Dodatkowo mówi krótki komunikat głosem.
     */
    suspend fun reportPreviousCrashIfAny(reason: String?) {
        val text = reason?.takeIf { it.isNotBlank() } ?: return
        _state.value = _state.value.copy(lastCrashReason = text)
        val spoken = "Uwaga. Poprzednim razem aplikacja zakończyła się niespodziewanie. Szczegóły są dostępne na ekranie Ster do skopiowania."
        _state.value = _state.value.copy(lastAnnouncement = spoken)
        speakCritical(spoken, settings)
    }

    fun clearCrashReason() {
        _state.value = _state.value.copy(lastCrashReason = null)
    }

    fun holdCurrentCourse() {
        val course = _state.value.snapshot?.course ?: return
        settingsStore.update {
            it.copy(target = TargetMode.COURSE, targetCourse = normalized(course))
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun clearAdminMessage() {
        _state.value = _state.value.copy(adminMessage = null)
    }

    fun runAdministrationAction(action: AdministrationAction) {
        scope.launch {
            _state.value = _state.value.copy(isBusy = true)
            try {
                apiClient.performAdministrationAction(action, settings)
                val message = when (action) {
                    AdministrationAction.CALIBRATE -> "Kalibracja uruchomiona."
                    AdministrationAction.REBOOT -> "Urządzenie rozpoczyna restart."
                }
                _state.value = _state.value.copy(adminMessage = message)
            } catch (e: Exception) {
                _state.value = _state.value.copy(adminMessage = e.message ?: "Błąd akcji.")
            } finally {
                _state.value = _state.value.copy(isBusy = false)
            }
        }
    }

    private suspend fun runLoop() {
        while (scope.isActive && loopJob?.isActive == true) {
            val now = System.currentTimeMillis()
            if (now - lastFetchAt >= statusIntervalMs) {
                lastFetchAt = now
                refreshSnapshot()
            }

            val currentSettings = settings
            val snapshot = _state.value.snapshot
            if (_state.value.isReadingEnabled && snapshot != null) {
                val readingDelayMs = ((if (currentSettings.readingOutput.key == "aria")
                    currentSettings.readingInterval else currentSettings.readingDelay) * 1000).toLong()

                if (!isReadingInProgress && now - lastReadAt >= readingDelayMs) {
                    lastReadAt = now
                    isReadingInProgress = true
                    readingJob = scope.launch { readOut(snapshot, currentSettings) }
                }

                val canSignal = currentSettings.soundSignalsEnabled &&
                    (!isReadingInProgress || !currentSettings.avoidSignalsOverlap)
                if (canSignal && !isSignalInProgress &&
                    now - lastSignalAt >= (currentSettings.toneDelay * 1000).toLong()
                ) {
                    lastSignalAt = now
                    isSignalInProgress = true
                    val previous = lastSignaledSnapshot
                    lastSignaledSnapshot = snapshot
                    signalJob = scope.launch { playSignal(snapshot, previous, currentSettings) }
                }
            }

            delay(loopDelayMs)
        }
    }

    private suspend fun refreshSnapshot() {
        try {
            val readings = retrying(3) { apiClient.fetchHelmReadings(settings) }
            val s = settings
            val course = readings.course(s.courseSource)
            val rudder = readings.rsa?.let { raw ->
                val corrected = raw + s.rudderAngleCorrection
                if (s.invertRudderAngle) -corrected else corrected
            }
            val recovered = _state.value.isConnectionLost

            _state.value = _state.value.copy(
                snapshot = HelmSnapshot(course, rudder, readings.wa, Date()),
                isConnectionLost = false,
                errorMessage = null
            )

            if (recovered) {
                onConnectionRecovered()
                if (_state.value.isReadingEnabled) {
                    val recoveryMessage = "Połączenie zostało przywrócone."
                    _state.value = _state.value.copy(lastAnnouncement = recoveryMessage)
                    speakCritical(recoveryMessage, s)
                }
            }
        } catch (e: Exception) {
            val message = "Utracono połączenie z urządzeniem BlueSeaEye. Sprawdź sieć Wi-Fi. Trwa ponawianie transmisji."
            val now = System.currentTimeMillis()
            val shouldAlert = !_state.value.isConnectionLost ||
                now - lastConnectionAlertAt >= connectionAlertRepeatIntervalMs

            _state.value = _state.value.copy(isConnectionLost = true, errorMessage = message)

            if (shouldAlert && _state.value.isReadingEnabled) {
                lastConnectionAlertAt = now
                alertAboutConnectionLoss(message)
            }
        }
    }

    private suspend fun readOut(snapshot: HelmSnapshot, settings: AppSettings) {
        try {
            val text = announcement(snapshot, settings)
            _state.value = _state.value.copy(lastAnnouncement = text)
            speakRegular(text, settings)
        } finally {
            isReadingInProgress = false
        }
    }

    private suspend fun alertAboutConnectionLoss(message: String) {
        _state.value = _state.value.copy(lastAnnouncement = message)
        if (_state.value.isReadingEnabled) {
            val s = settings
            tonePlayer.playAlertPattern(s.toneVolume / 100.0, s.toneType)
            speakCritical(message, s)
        }
        onConnectionLostAlert(message)
    }

    private suspend fun playSignal(
        snapshot: HelmSnapshot,
        previousSnapshot: HelmSnapshot?,
        settings: AppSettings
    ) {
        try {
            val currentValue = snapshot.course ?: return
            val targetValue = if (settings.target == TargetMode.COURSE) settings.targetCourse else null

            val delta = when {
                targetValue != null -> HelmMath.relativeCourse(currentValue, targetValue)
                previousSnapshot?.course != null -> HelmMath.relativeCourse(currentValue, previousSnapshot.course)
                else -> return
            }

            val absoluteDelta = abs(delta)
            val errorExceeded = absoluteDelta > settings.errorThreshold
            val onTarget = targetValue != null

            if (!(errorExceeded || settings.toneOnCourse || !onTarget)) return

            if (errorExceeded || (!onTarget && delta != 0.0)) {
                val compensatedDelta = absoluteDelta - (if (onTarget) settings.errorThreshold else 0.0)
                val severity = minOf(compensatedDelta, settings.errorRange)
                val gain = if (delta > 0) 1.0 else -1.0
                val multiplier = if (settings.broadTonalSpread) 2.0 else 1.0
                if (settings.referenceTone) {
                    tonePlayer.play(frequencyMid, 0.08, settings.toneVolume / 100.0, settings.toneType)
                    delay(20)
                }
                val baseOffset = settings.toneBaseOffset / 12.0
                val frequency = frequencyMid * 2.0.pow(
                    gain * ((multiplier * severity / settings.errorRange) + baseOffset)
                )
                tonePlayer.play(frequency, 0.1, settings.toneVolume / 100.0, settings.toneType)
            } else {
                tonePlayer.play(frequencyMid, 0.1, settings.toneVolume / 100.0, settings.toneType)
            }
        } finally {
            isSignalInProgress = false
        }
    }

    private fun announcement(snapshot: HelmSnapshot, settings: AppSettings): String {
        val parts = mutableListOf<String>()
        val value = snapshot.displayedValue(settings)
        val mainText = if (value != null) {
            if (settings.target == TargetMode.COURSE) "Odchyłka $value" else "Kurs $value"
        } else {
            if (settings.target == TargetMode.COURSE) "Odchyłka nieznana" else "Kurs nieznany"
        }
        parts.add(mainText)
        snapshot.rudder?.let {
            val side = if (it >= 0) "prawo" else "lewo"
            parts.add("Ster $side ${abs(it.roundToInt())}")
        }
        snapshot.wind?.let { parts.add("Wiatr ${it.roundToInt()}") }
        return parts.joinToString(", ")
    }

    private suspend fun <T> retrying(times: Int, operation: suspend () -> T): T {
        var attemptsLeft = times
        while (attemptsLeft > 1) {
            try {
                return operation()
            } catch (_: Exception) {
                attemptsLeft--
            }
        }
        return operation()
    }

    private suspend fun speakRegular(text: String, settings: AppSettings) {
        if (isSpeechActive) return
        speechGeneration++
        val generation = speechGeneration
        isSpeechActive = true
        speaker.announce(text, settings)
        if (speechGeneration == generation) {
            isSpeechActive = false
        }
    }

    private suspend fun speakCritical(text: String, settings: AppSettings) {
        speechGeneration++
        val generation = speechGeneration
        speaker.stop()
        isSpeechActive = true
        speaker.announceCritical(text, settings)
        if (speechGeneration == generation) {
            isSpeechActive = false
        }
    }

    private fun normalized(course: Double): Double = HelmMath.normalizedCourse(course)
}
