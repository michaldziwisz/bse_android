package eu.blueseaeye.bse.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import eu.blueseaeye.bse.model.ToneWaveform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Generator tonów odchyłki. Syntetyzuje PCM 16-bit mono i odtwarza przez
 * AudioTrack. Kształty fal i obwiednia (sinusoidalne wyciszenie na krańcach)
 * odpowiadają wersji iOS (TonePlayer).
 */
class TonePlayer {
    private val sampleRate = 44_100
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

    suspend fun play(
        frequency: Double,
        durationSeconds: Double = 0.1,
        volume: Double,
        waveform: ToneWaveform
    ) = withContext(Dispatchers.Default) {
        if (durationSeconds <= 0) return@withContext
        runCatching {
            stop()
            val samples = makeToneSamples(frequency, durationSeconds, volume, waveform)
            val track = buildTrack(samples.size)
            activeTrack = track
            track.write(samples, 0, samples.size)
            track.play()
            delay((durationSeconds * 1000).toLong())
            if (activeTrack === track) {
                stop()
            }
        }
    }

    suspend fun playAlertPattern(volume: Double, waveform: ToneWaveform) {
        val alertVolume = maxOf(volume, 0.85)
        val frequencies = listOf(1320.0, 880.0, 1320.0)
        for (frequency in frequencies) {
            play(frequency, 0.22, alertVolume, waveform)
            delay(100)
        }
    }

    private fun buildTrack(frameCount: Int): AudioTrack {
        val bufferSizeBytes = frameCount * 2
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
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
    }

    private fun makeToneSamples(
        frequency: Double,
        durationSeconds: Double,
        volume: Double,
        waveform: ToneWaveform
    ): ShortArray {
        val frameCount = (sampleRate * durationSeconds).toInt().coerceAtLeast(1)
        val amplitude = volume.coerceIn(0.0, 1.0) * 0.9
        val samples = ShortArray(frameCount)
        val maxAbs = Short.MAX_VALUE.toDouble()
        for (frame in 0 until frameCount) {
            val time = frame.toDouble() / sampleRate
            val progress = frame.toDouble() / maxOf(frameCount - 1, 1)
            val envelope = sin(PI * progress)
            val phase = 2 * PI * frequency * time
            val value = sample(phase, waveform) * amplitude * envelope
            val scaled = (value * maxAbs).coerceIn(Short.MIN_VALUE.toDouble(), maxAbs)
            samples[frame] = scaled.toInt().toShort()
        }
        return samples
    }

    private fun sample(phase: Double, waveform: ToneWaveform): Double = when (waveform) {
        ToneWaveform.SINE -> sin(phase)
        ToneWaveform.TRIANGLE -> 2 * abs(2 * ((phase / (2 * PI)) % 1) - 1) - 1
        ToneWaveform.SAWTOOTH -> 2 * ((phase / (2 * PI)) % 1) - 1
        ToneWaveform.SQUARE -> if (sin(phase) >= 0) 1.0 else -1.0
    }
}
