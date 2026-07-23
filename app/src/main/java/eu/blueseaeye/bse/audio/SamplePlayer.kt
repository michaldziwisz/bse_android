package eu.blueseaeye.bse.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.content.Context
import android.util.Log
import androidx.annotation.RawRes
import eu.blueseaeye.bse.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Odtwarzacz sygnałów odchyłki oparty o gotowe próbki dźwiękowe (WAV mono
 * 16-bit) zamiast syntezowanych tonów. Trzy próbki:
 *  - [Signal.CENTER] (0.wav) — na kursie,
 *  - [Signal.LEFT]   (l1.wav) — odchyłka w lewo („lewiej”),
 *  - [Signal.RIGHT]  (r1.wav) — odchyłka w prawo („prawiej”).
 *
 * Wysokość dźwięku podnosimy wraz z wielkością odchyłki — dokładnie tak jak
 * dawniej robił to generator tonów. Podnosimy WYŁĄCZNIE wysokość (pitch),
 * ZACHOWUJĄC długość i tempo próbki: używamy [PlaybackParams.setPitch] przy
 * [PlaybackParams.setSpeed] = 1.0. Pod spodem framework (Sonic) rozciąga w
 * czasie, więc próbka nie jest „przyspieszona”, tylko wyższa. Dostępne od API 23
 * (minSdk 26 — bez problemu).
 *
 * Próbki dekodujemy raz i trzymamy w pamięci (są krótkie: 60–110 ms).
 */
class SamplePlayer(context: Context) {

    enum class Signal(@RawRes val resId: Int) {
        CENTER(R.raw.sig_center),
        LEFT(R.raw.sig_left),
        RIGHT(R.raw.sig_right)
    }

    private data class Pcm(val samples: ShortArray, val sampleRate: Int)

    private val appContext = context.applicationContext
    private val cache = HashMap<Signal, Pcm>()

    private companion object {
        const val TAG = "SamplePlayer"
    }

    @Volatile
    private var activeTrack: AudioTrack? = null

    fun stop() {
        activeTrack?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        activeTrack = null
    }

    /**
     * Odtwarza wskazaną próbkę [signal] z zadaną wysokością ([pitchRatio], 1.0 =
     * naturalna wysokość, 2.0 = oktawa wyżej) i głośnością [volume] (0.0–1.0).
     * Długość i tempo próbki pozostają bez zmian — zmienia się wyłącznie wysokość.
     *
     * [pan] rozrzuca dźwięk po kanałach stereo: 0.0 = środek, wartość ujemna =
     * bardziej w lewo, dodatnia = bardziej w prawo (zakres -1.0…1.0, gdzie ±1.0
     * to skrajna strona). Realizujemy to przyciszając kanał przeciwny do strony
     * (mono próbka trafia do obu kanałów, a [android.media.AudioTrack.setStereoVolume]
     * nadaje im różne wzmocnienia). Dzięki temu z podłączonymi obiema słuchawkami
     * sygnał „lewy” słychać bardziej po lewej, a „prawy” po prawej.
     */
    suspend fun play(
        signal: Signal,
        pitchRatio: Double,
        volume: Double,
        pan: Double = 0.0
    ) = withContext(Dispatchers.Default) {
        try {
            val pcm = load(signal)
            if (pcm == null) {
                Log.e(TAG, "play($signal): brak zdekodowanej probki (load=null)")
                return@withContext
            }
            stop()
            val output = scaleVolume(pcm.samples, volume)
            if (output.isEmpty()) {
                Log.e(TAG, "play($signal): pusta probka po skalowaniu (samples=${pcm.samples.size})")
                return@withContext
            }
            val track = buildTrack(pcm.sampleRate, output.size)
            activeTrack = track

            // Panorama: mono próbka trafia do obu kanałów, różne wzmocnienia lewego
            // i prawego przesuwają dźwięk w bok. pan<0 => ciszej po prawej (dźwięk
            // po lewej), pan>0 => ciszej po lewej. |pan| do 1.0 = skrajna strona.
            val clampedPan = pan.coerceIn(-1.0, 1.0)
            if (clampedPan != 0.0) {
                val leftGain = (if (clampedPan > 0) 1.0 - clampedPan else 1.0).toFloat().coerceIn(0f, 1f)
                val rightGain = (if (clampedPan < 0) 1.0 + clampedPan else 1.0).toFloat().coerceIn(0f, 1f)
                @Suppress("DEPRECATION")
                runCatching { track.setStereoVolume(leftGain, rightGain) }
                    .onFailure { Log.w(TAG, "play($signal): setStereoVolume nieudane: ${it.message}") }
            }

            val written = track.write(output, 0, output.size)
            if (written < 0) {
                Log.e(TAG, "play($signal): write blad=$written")
                stop()
                return@withContext
            }

            // Pitch (jesli >1) ustawiamy PRZED play na tracku STATIC. Gdy sie nie
            // uda (limity urzadzenia) — probka gra w naturalnej wysokosci.
            val pitch = pitchRatio.coerceIn(0.5, 4.0).toFloat()
            if (pitch != 1.0f) {
                runCatching {
                    track.playbackParams = track.playbackParams
                        .setPitch(pitch)
                        .setSpeed(1.0f)
                }.onFailure { Log.w(TAG, "play($signal): setPitch($pitch) nieudane: ${it.message}") }
            }

            track.play()

            val durationMs = (output.size.toLong() * 1000L) / pcm.sampleRate.coerceAtLeast(1)
            delay(durationMs + 40L)
            if (activeTrack === track) {
                stop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "play($signal) wyjatek: ${e.message}", e)
        }
    }

    private fun scaleVolume(input: ShortArray, volume: Double): ShortArray {
        val gain = volume.coerceIn(0.0, 1.0)
        if (gain >= 1.0) return input
        val out = ShortArray(input.size)
        val maxAbs = Short.MAX_VALUE.toDouble()
        for (i in input.indices) {
            val scaled = (input[i].toDouble() * gain).coerceIn(-maxAbs, maxAbs)
            out[i] = scaled.toInt().toShort()
        }
        return out
    }

    private fun buildTrack(sampleRate: Int, frameCount: Int): AudioTrack {
        // Bufor = dokladny rozmiar probki (MODE_STATIC wymaga zmieszczenia calej
        // probki w buforze; dokladnie jak dzialajacy TonePlayer).
        val bufferSizeBytes = (frameCount * 2).coerceAtLeast(1)

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
    }

    @Synchronized
    private fun load(signal: Signal): Pcm? {
        cache[signal]?.let { return it }
        val decoded = runCatching { decodeWav(signal.resId) }.getOrNull() ?: return null
        cache[signal] = decoded
        return decoded
    }

    /**
     * Minimalny dekoder WAV PCM (16-bit mono little-endian) z zasobu raw.
     * Próbki są naszymi własnymi plikami w znanym formacie (44,1 kHz mono 16-bit),
     * więc czytamy chunk `fmt ` dla częstotliwości i chunk `data` dla PCM.
     */
    private fun decodeWav(@RawRes resId: Int): Pcm {
        val bytes = appContext.resources.openRawResource(resId).use { input ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var read = input.read(chunk)
            while (read >= 0) {
                buffer.write(chunk, 0, read)
                read = input.read(chunk)
            }
            buffer.toByteArray()
        }

        fun u16(offset: Int): Int =
            (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

        fun u32(offset: Int): Int =
            (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)

        var sampleRate = 44_100
        var dataOffset = -1
        var dataSize = 0

        // Nagłówek RIFF/WAVE, potem sekwencja chunków od offsetu 12.
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = u32(pos + 4)
            val body = pos + 8
            when (id) {
                "fmt " -> if (body + 16 <= bytes.size) {
                    sampleRate = u32(body + 4)
                }
                "data" -> {
                    dataOffset = body
                    dataSize = size.coerceAtMost(bytes.size - body)
                }
            }
            if (dataOffset >= 0) break
            // Chunki są wyrównane do parzystej liczby bajtów.
            pos = body + size + (size and 1)
        }

        if (dataOffset < 0) return Pcm(ShortArray(0), sampleRate)

        val frameCount = dataSize / 2
        val samples = ShortArray(frameCount)
        var i = 0
        var b = dataOffset
        while (i < frameCount) {
            samples[i] = ((bytes[b].toInt() and 0xFF) or (bytes[b + 1].toInt() shl 8)).toShort()
            i++
            b += 2
        }
        // NORMALIZACJA do pelnej skali. Dostarczone nagrania sa ciche u zrodla
        // (peak ~45-50% FS, RMS ~13-19%), a mnozone jeszcze przez ustawienie
        // glosnosci (~20%) i systemowa glosnosc multimediow schodza do ~-45 dB =
        // praktycznie cisza (log z S25: track gra, ale niesłyszalny). Dawny generator
        // tonow gral pelna fala (0.9 FS), stad byl slyszalny przy tych samych
        // ustawieniach. Podbijamy szczyt kazdej probki do 0.97 FS - wyrownuje tez
        // glosnosc miedzy 0/l1/r1 (kazdy mial inny peak).
        var peak = 0
        for (s in samples) {
            val a = if (s.toInt() == Short.MIN_VALUE.toInt()) Short.MAX_VALUE.toInt() else kotlin.math.abs(s.toInt())
            if (a > peak) peak = a
        }
        if (peak in 1 until 31785) { // 31785 ~= 0.97 * 32767; ponizej = warto podbic
            val gain = (0.97 * Short.MAX_VALUE) / peak
            val maxAbs = Short.MAX_VALUE.toDouble()
            for (j in samples.indices) {
                val v = (samples[j].toDouble() * gain).coerceIn(-maxAbs, maxAbs)
                samples[j] = v.toInt().toShort()
            }
        }
        return Pcm(samples, sampleRate)
    }
}
