package eu.blueseaeye.bse.audio

import android.content.Context
import android.media.session.MediaSession
import android.media.session.PlaybackState

/**
 * Sesja multimediów, dzięki której gest czytnika ekranu „magiczne stuknięcie”
 * (TalkBack: dwukrotne stuknięcie dwoma palcami) włącza i wyłącza odczyt —
 * odpowiednik `.accessibilityAction(.magicTap)` z wersji iOS.
 *
 * TalkBack kieruje magiczne stuknięcie do aktywnej sesji multimediów jako
 * odtwórz/wstrzymaj. Utrzymujemy więc aktywną sesję z akcjami PLAY/PAUSE/STOP,
 * a każde z tych zdarzeń przełącza odczyt (jak w iOS, gdzie onPlay/onPause/onToggle
 * wszystkie wołają toggleReading).
 */
class HelmMediaSession(context: Context, private val onToggle: () -> Unit) {

    private val session = MediaSession(context.applicationContext, "BSE").apply {
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() = onToggle()
            override fun onPause() = onToggle()
            override fun onStop() = onToggle()
        })
        isActive = true
    }

    /**
     * Aktualizuje stan odtwarzania, żeby czytnik wiedział, że sesja jest
     * sterowalna (odtwarzanie w trakcie odczytu, pauza poza nim). Dostępne akcje
     * obejmują zawsze PLAY, PAUSE, PLAY_PAUSE i STOP, aby magiczne stuknięcie
     * działało niezależnie od bieżącego stanu.
     */
    fun updatePlaybackState(isReading: Boolean) {
        val state = if (isReading) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val playbackState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_STOP
            )
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build()
        session.setPlaybackState(playbackState)
    }

    fun release() {
        session.isActive = false
        session.release()
    }
}
