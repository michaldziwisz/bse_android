package eu.blueseaeye.bse.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.content.Context
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
     */
    suspend fun play(
        signal: Signal,
        pitchRatio: Double,
        volume: Double
    ) = withContext(Dispatchers.Default) {
        runCatching {
            val pcm = load(signal) ?: return@withContext
            stop()
            val output = scaleVolume(pcm.samples, volume)
            if (output.isEmpty()) return@withContext
            val track = buildTrack(pcm.sampleRate, output.size)
            activeTrack = track

            // Podnosimy TYLKO wysokość, tempo zostaje (speed = 1.0). Pitch poza
            // obsługiwanym zakresem rzuciłby wyjątek — clamp + runCatching chroni;
            // gdyby się nie udało, próbka i tak zagra w naturalnej wysokości.
            val pitch = pitchRatio.coerceIn(0.5, 4.0).toFloat()
            runCatching {
                track.playbackParams = PlaybackParams()
                    .setPitch(pitch)
                    .setSpeed(1.0f)
            }

            track.write(output, 0, output.size)
            track.play()
            // Czas trwania odtwarzania = długość próbki (speed = 1.0 nie zmienia
            // tempa; pitch rozciągany jest w czasie tak, by długość została).
            val durationMs = (output.size.toLong() * 1000L) / pcm.sampleRate.coerceAtLeast(1)
            delay(durationMs)
            if (activeTrack === track) {
                stop()
            }
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
        val bufferSizeBytes = (frameCount * 2).coerceAtLeast(1)
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(bufferSizeBytes)

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
            .setBufferSizeInBytes(minBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
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
        return Pcm(samples, sampleRate)
    }
}
