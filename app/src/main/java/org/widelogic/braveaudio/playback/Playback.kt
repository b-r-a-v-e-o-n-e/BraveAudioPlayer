package org.widelogic.braveaudio.playback

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

object PlaybackHolder {
    @Volatile private var playerInternal: ExoPlayer? = null
    val player: ExoPlayer get() = requireNotNull(playerInternal) { "PlaybackHolder not initialized" }

    fun init(context: Context): ExoPlayer {
        return playerInternal ?: synchronized(this) {
            playerInternal ?: ExoPlayer.Builder(context.applicationContext).build().also { p ->
                p.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                p.playWhenReady = false
                playerInternal = p
            }
        }
    }

    fun mediaItem(uri: String, title: String, artist: String = "") =
        MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(title).setArtist(artist).build()
            )
            .build()

    fun release() {
        playerInternal?.release()
        playerInternal = null
    }
}

class EqualizerController(private val player: ExoPlayer) {
    val frequencies = listOf("60 Hz", "170 Hz", "310 Hz", "1 kHz", "3 kHz", "12 kHz")
    private var eq: Equalizer? = null
    private var connectedSession = -1
    private val _levels = MutableStateFlow(List(6) { 0f })
    val levels: StateFlow<List<Float>> = _levels

    fun attach() {
        val sessionId = player.audioSessionId
        if (eq != null && sessionId == connectedSession) return
        release()
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        eq = runCatching { Equalizer(0, sessionId) }.getOrNull()?.apply { enabled = true }
        if (eq != null) connectedSession = sessionId
        applyAll()
    }

    fun setBand(index: Int, normalized: Float) {
        val copy = _levels.value.toMutableList()
        copy[index] = normalized.coerceIn(-1f, 1f)
        _levels.value = copy
        applyBand(index)
    }

    private fun applyAll() = (0 until 6).forEach(::applyBand)

    private fun applyBand(index: Int) {
        val e = eq ?: return
        if (e.numberOfBands <= 0) return
        val targetsHz = intArrayOf(60, 170, 310, 1000, 3000, 12000)
        val targetMilliHz = targetsHz[index] * 1000
        val band = (0 until e.numberOfBands.toInt()).minByOrNull { b ->
            abs(e.getCenterFreq(b.toShort()) - targetMilliHz)
        }?.toShort() ?: return
        val range = e.bandLevelRange
        val min = range[0].toInt()
        val max = range[1].toInt()
        val value = if (_levels.value[index] >= 0) {
            (_levels.value[index] * max).toInt()
        } else {
            (-_levels.value[index] * min).toInt()
        }.coerceIn(min, max).toShort()
        runCatching { e.setBandLevel(band, value) }
    }

    fun release() {
        runCatching { eq?.release() }
        eq = null
        connectedSession = -1
    }
}

class AudioVisualizer(private val player: ExoPlayer) {
    private var visualizer: Visualizer? = null
    private val _fft = MutableStateFlow(FloatArray(32))
    val fft: StateFlow<FloatArray> = _fft

    fun start() {
        stop()
        val sessionId = player.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        visualizer = runCatching {
            Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0].coerceAtLeast(128)
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null || fft.size < 4) return
                        val bins = FloatArray(32)
                        val usable = minOf(32, fft.size / 2 - 1)
                        for (i in 0 until usable) {
                            val re = fft[2 * i + 2].toInt()
                            val im = fft[2 * i + 3].toInt()
                            bins[i] = kotlin.math.sqrt((re * re + im * im).toFloat()) / 128f
                        }
                        _fft.value = bins
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        }.getOrNull()
    }

    fun stop() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        _fft.value = FloatArray(32)
    }
}

object AudioEffects {
    private var controller: EqualizerController? = null
    fun equalizer(player: ExoPlayer): EqualizerController =
        controller ?: EqualizerController(player).also { controller = it }
    fun release() {
        controller?.release()
        controller = null
    }
}
