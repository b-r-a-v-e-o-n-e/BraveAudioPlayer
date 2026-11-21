package org.widelogic.braveaudio.playback

import android.content.Context
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Visualizer
import android.os.Build
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/** Single process-owned player. Save the current queue for recovery after process recreation. */
object PlaybackHolder {
    @Volatile private var playerInternal: ExoPlayer? = null
    private var failures = 0
    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage
    val player: ExoPlayer get() = requireNotNull(playerInternal) { "Player has not been initialized" }

    fun init(context: Context): ExoPlayer {
        playerInternal?.let { return it }
        return synchronized(this) {
            playerInternal ?: ExoPlayer.Builder(context.applicationContext).build().also { p ->
                p.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
                )
                p.setHandleAudioBecomingNoisy(true)
                p.repeatMode = Player.REPEAT_MODE_OFF
                val prefs = context.applicationContext.getSharedPreferences("brave_audio_queue", Context.MODE_PRIVATE)
                var restoring = false
                fun persist() {
                    if (restoring || p.mediaItemCount == 0) return
                    runCatching {
                        val items = JSONArray()
                        for (i in 0 until p.mediaItemCount) {
                            val item = p.getMediaItemAt(i)
                            val uri = item.localConfiguration?.uri?.toString() ?: continue
                            items.put(JSONObject().put("uri", uri)
                                .put("title", item.mediaMetadata.title?.toString().orEmpty())
                                .put("artist", item.mediaMetadata.artist?.toString().orEmpty()))
                        }
                        prefs.edit().putString("items", items.toString())
                            .putInt("index", p.currentMediaItemIndex.coerceAtLeast(0))
                            .putLong("position", p.currentPosition.coerceAtLeast(0))
                            .apply()
                    }
                }
                p.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                        failures = 0
                        _errorMessage.value = ""
                        persist()
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) { persist() }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            failures = 0
                            _errorMessage.value = ""
                        }
                        if (playbackState == Player.STATE_ENDED) persist()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        _errorMessage.value = "Playback error: ${error.errorCodeName}"
                        // Retry decoder/IO errors at most once per failure episode.
                        if (failures++ == 0 && p.mediaItemCount > 0) {
                            val at = p.currentPosition.coerceAtLeast(0)
                            p.prepare()
                            p.seekTo(at)
                            p.playWhenReady = true
                        }
                    }
                })
                restoring = true
                runCatching {
                    val saved = JSONArray(prefs.getString("items", "[]"))
                    val items = (0 until saved.length()).map { i ->
                        val obj = saved.getJSONObject(i)
                        mediaItem(obj.getString("uri"), obj.optString("title"), obj.optString("artist"))
                    }
                    if (items.isNotEmpty()) {
                        val index = prefs.getInt("index", 0).coerceIn(0, items.lastIndex)
                        p.setMediaItems(items, index, prefs.getLong("position", 0).coerceAtLeast(0))
                        p.playWhenReady = false
                        p.prepare()
                    }
                }.onFailure { Log.w("BraveAudio", "Queue restore failed", it) }
                restoring = false
                playerInternal = p
            }
        }
    }

    fun mediaItem(uri: String, title: String, artist: String = "") =
        MediaItem.Builder().setUri(uri).setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist).build()
        ).build()

    fun togglePlaybackSafely(player: Player) {
        if (player.mediaItemCount == 0) return
        when {
            player.isPlaying || (player.playWhenReady && player.playbackState != Player.STATE_ENDED) -> player.pause()
            player.playbackState == Player.STATE_ENDED -> {
                val index = player.currentMediaItemIndex.coerceAtLeast(0)
                player.seekTo(index, 0L)
                player.prepare()
                player.play()
            }
            player.playerError != null || player.playbackState == Player.STATE_IDLE -> {
                failures = 0
                player.prepare()
                player.play()
            }
            else -> player.play()
        }
    }

    /** Position checkpoint prevents losing progress after a long pause or Android process kill. */
    fun checkpoint(context: Context) {
        val p = playerInternal ?: return
        if (p.mediaItemCount == 0) return
        context.applicationContext.getSharedPreferences("brave_audio_queue", Context.MODE_PRIVATE)
            .edit().putInt("index", p.currentMediaItemIndex.coerceAtLeast(0))
            .putLong("position", p.currentPosition.coerceAtLeast(0)).apply()
    }

    fun release() {
        playerInternal?.release()
        playerInternal = null
        AudioEffects.release()
    }
}

class EqualizerController(private val player: ExoPlayer) {
    val frequencies = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    private val cutoffs = floatArrayOf(44f, 88f, 177f, 354f, 707f, 1414f, 2828f, 5657f, 11314f, 20000f)
    private val centersHz = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    private var dynamics: DynamicsProcessing? = null
    private var fallback: Equalizer? = null
    private var attachedSession = -1
    private val _levels = MutableStateFlow(List(10) { 0f })
    val levels: StateFlow<List<Float>> = _levels
    private val _status = MutableStateFlow("Waiting for audio session")
    val status: StateFlow<String> = _status

    fun attach() {
        val session = player.audioSessionId
        if (session == C.AUDIO_SESSION_ID_UNSET || session <= 0) return
        if (attachedSession == session && (dynamics != null || fallback != null)) return
        release()
        attachedSession = session
        if (Build.VERSION.SDK_INT >= 28) {
            dynamics = runCatching {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, true, 10, false, 1, false, 1, true
                ).build()
                for (band in 0 until 10) {
                    config.setPreEqBandAllChannelsTo(band, DynamicsProcessing.EqBand(true, cutoffs[band], 0f))
                }
                DynamicsProcessing(0, session, config).apply { enabled = true }
            }.onFailure { Log.w("BraveAudio", "10-band DSP unavailable", it) }.getOrNull()
        }
        if (dynamics == null) {
            fallback = runCatching { Equalizer(0, session).apply { enabled = true } }.getOrNull()
        }
        _status.value = when {
            dynamics != null -> "10 independent DSP bands"
            fallback != null -> "Compatibility EQ: ${fallback!!.numberOfBands} device bands (10 controls mapped)"
            else -> "Equalizer unavailable on this device"
        }
        applyAll()
    }

    fun setBand(index: Int, value: Float) {
        if (index !in 0 until 10) return
        val newLevels = _levels.value.toMutableList()
        newLevels[index] = value.coerceIn(-1f, 1f)
        _levels.value = newLevels
        // Some legacy devices expose fewer native bands; calculate all combined values.
        applyAll()
    }

    private fun applyAll() {
        val levels = _levels.value
        dynamics?.let { dsp ->
            for (i in 0 until 10) runCatching {
                dsp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoffs[i], levels[i] * 12f))
            }
        }
        fallback?.let { eq ->
            val count = eq.numberOfBands.toInt()
            if (count <= 0) return@let
            val sum = FloatArray(count)
            val hits = IntArray(count)
            for (i in levels.indices) {
                val nearest = (0 until count).minByOrNull { band ->
                    abs(eq.getCenterFreq(band.toShort()) / 1000f - centersHz[i])
                } ?: continue
                sum[nearest] += levels[i]
                hits[nearest]++
            }
            val range = eq.bandLevelRange
            for (band in 0 until count) {
                val normalized = if (hits[band] == 0) 0f else sum[band] / hits[band]
                val value = if (normalized >= 0f) normalized * range[1] else -normalized * range[0]
                runCatching { eq.setBandLevel(band.toShort(), value.toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()) }
            }
        }
    }

    fun release() {
        runCatching { dynamics?.release() }
        runCatching { fallback?.release() }
        dynamics = null
        fallback = null
        attachedSession = -1
    }
}

/** Explicit, persisted capture profiles. Low uses fewer FFT samples/bands and quicker updates.
 * High favors frequency resolution. Neither profile synthesizes spectral content.
 */
enum class VisualizationQuality(val title: String, val description: String, val bandCount: Int) {
    LOW_REALTIME("Low Realtime", "Responsive 256-point FFT • 32 bands • reduced CPU", 32),
    HIGH_FIDELITY("High Fidelity", "High-resolution FFT • 96 bands • higher CPU", 96)
}

object VisualizationPreferences {
    private const val FILE = "brave_visualization"
    private const val KEY = "quality"
    private val _quality = MutableStateFlow(VisualizationQuality.LOW_REALTIME)
    val quality: StateFlow<VisualizationQuality> = _quality
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val stored = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, VisualizationQuality.LOW_REALTIME.name)
        _quality.value = VisualizationQuality.entries.firstOrNull { it.name == stored }
            ?: VisualizationQuality.LOW_REALTIME
        loaded = true
    }

    fun select(context: Context, newValue: VisualizationQuality) {
        load(context)
        _quality.value = newValue
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY, newValue.name).apply()
    }
}

/** FFT is captured from ExoPlayer's audio session, not simulated animation data.
 * SpectrumMath returns approximate dBFS in logarithmic frequency bands.
 * Android Visualizer's capture rate is specified in millihertz, not hertz.
 */
class AudioVisualizer(private val player: ExoPlayer) {
    private var visualizer: Visualizer? = null
    private var connectedSession = -1
    private var currentQuality: VisualizationQuality? = null
    private val _fft = MutableStateFlow(FloatArray(32) { -96f })
    val fft: StateFlow<FloatArray> = _fft
    private val _sampleRate = MutableStateFlow(0)
    val sampleRate: StateFlow<Int> = _sampleRate

    fun start(quality: VisualizationQuality = VisualizationQuality.LOW_REALTIME) {
        val session = player.audioSessionId
        if (session == C.AUDIO_SESSION_ID_UNSET || session <= 0) return
        if (visualizer != null && session == connectedSession && quality == currentQuality) return
        stop()
        _fft.value = FloatArray(quality.bandCount) { -96f }
        visualizer = runCatching {
            Visualizer(session).apply {
                val bounds = Visualizer.getCaptureSizeRange()
                // 256 samples at 44.1 kHz resolve only ~172 Hz/bin: many bass bars
                // were repeated representations of the *same* FFT sample.
                // 1024 is still cheap to capture and resolves ~43 Hz/bin.
                val requested = if (quality == VisualizationQuality.LOW_REALTIME) {
                    min(bounds[1], 1024).coerceAtLeast(bounds[0])
                } else bounds[1]
                // Capture size must be a supported power of two.
                captureSize = requested.coerceIn(bounds[0], bounds[1])
                val rate = if (quality == VisualizationQuality.LOW_REALTIME) {
                    Visualizer.getMaxCaptureRate() // strongest responsiveness
                } else {
                    (Visualizer.getMaxCaptureRate() * 3 / 4).coerceAtLeast(1000)
                }
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null || fft.size < 8) return
                        // No allocation for duplicate/larger bands in low-resource mode.
                        val sampleHz = samplingRate / 1000f
                        if (sampleHz <= 0f) return
                        _sampleRate.value = sampleHz.toInt()
                        _fft.value = SpectrumMath.logBands(fft, sampleHz, quality.bandCount)
                    }
                }, rate, false, true)
                enabled = true
            }
        }.onFailure { Log.w("BraveAudio", "Visualizer unavailable", it) }.getOrNull()
        if (visualizer != null) {
            connectedSession = session
            currentQuality = quality
        }
    }

    fun stop() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        val previousBandCount = currentQuality?.bandCount ?: 32
        connectedSession = -1
        currentQuality = null
        // Clear immediately on pause/end/stop instead of leaving a frozen high spectrum.
        _fft.value = FloatArray(previousBandCount) { -96f }
        _sampleRate.value = 0
    }
}

/** Math extracted for local regression tests. No synthetic/random spectrum values. */
object SpectrumMath {
    fun logBands(fft: ByteArray, sampleRateHz: Float, outputCount: Int): FloatArray {
        val n = fft.size
        if (n < 8 || sampleRateHz <= 0f || outputCount <= 0) return FloatArray(outputCount.coerceAtLeast(0)) { -96f }
        val half = n / 2
        val nyquist = sampleRateHz / 2f
        val maxHz = min(20000f, nyquist)
        val floorHz = 30f
        val bins = FloatArray(half + 1)
        bins[0] = abs(fft[0].toInt()).toFloat()
        bins[half] = abs(fft[1].toInt()).toFloat()
        for (k in 1 until half) {
            val real = fft[2 * k].toInt().toFloat()
            val imaginary = fft[2 * k + 1].toInt().toFloat()
            bins[k] = hypot(real, imaginary)
        }
        return FloatArray(outputCount) { i ->
            val leftHz = floorHz * (maxHz / floorHz).pow(i / outputCount.toFloat())
            val rightHz = floorHz * (maxHz / floorHz).pow((i + 1f) / outputCount)
            val first = max(1, floor(leftHz * n / sampleRateHz).toInt())
            val last = min(half, ceil(rightHz * n / sampleRateHz).toInt())
            // RMS magnitude within this frequency band. Using the max() of a
            // wide band exaggerated sustained energy and held bars near the top.
            var power = 0f
            var measured = 0
            if (first <= last) {
                for (k in first..last) {
                    power += bins[k] * bins[k]
                    measured++
                }
            }
            val rms = if (measured > 0) sqrt(power / measured) else 0f
            // Approximate dBFS; Android Visualizer data is 8-bit and may be
            // processed by the device. No invented/random spectrum data.
            (20f * log10(max(rms, 0.001f) / 181f)).coerceIn(-96f, 0f)
        }
    }
}

class GainController(private val context: Context, private val player: ExoPlayer) {
    private var enhancer: LoudnessEnhancer? = null
    private var connectedSession = -1
    private val prefs = context.applicationContext.getSharedPreferences("brave_audio_gain", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean("enabled", false))
    private val _percentage = MutableStateFlow(prefs.getInt("percentage", 100).coerceIn(100, 300))
    val enabled: StateFlow<Boolean> = _enabled
    val percentage: StateFlow<Int> = _percentage
    private val _available = MutableStateFlow(true)
    val available: StateFlow<Boolean> = _available

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        prefs.edit().putBoolean("enabled", value).apply()
        apply()
    }
    fun setPercentage(value: Int) {
        _percentage.value = value.coerceIn(100, 300)
        prefs.edit().putInt("percentage", _percentage.value).apply()
        apply()
    }
    fun attach() {
        val session = player.audioSessionId
        if (session == C.AUDIO_SESSION_ID_UNSET || session <= 0) return
        if (session == connectedSession && enhancer != null) return
        release()
        connectedSession = session
        enhancer = runCatching { LoudnessEnhancer(session) }.onFailure {
            Log.w("BraveAudio", "LoudnessEnhancer unavailable", it)
        }.getOrNull()
        _available.value = enhancer != null
        apply()
    }
    private fun apply() {
        enhancer?.let { effect ->
            runCatching {
                val gainDb = 20 * log10(_percentage.value / 100.0)
                effect.setTargetGain((gainDb * 100).roundToInt()) // millibels
                effect.enabled = _enabled.value && _percentage.value > 100
            }.onFailure { Log.w("BraveAudio", "Gain update failed", it) }
        }
    }
    fun release() {
        runCatching { enhancer?.release() }
        enhancer = null
        connectedSession = -1
    }
}

object AudioEffects {
    private var eqController: EqualizerController? = null
    private var gainController: GainController? = null
    fun equalizer(player: ExoPlayer): EqualizerController =
        eqController ?: EqualizerController(player).also { eqController = it }
    fun gain(context: Context, player: ExoPlayer): GainController =
        gainController ?: GainController(context.applicationContext, player).also { gainController = it }
    fun attach(context: Context, player: ExoPlayer) {
        equalizer(player).attach()
        gain(context, player).attach()
    }
    fun release() {
        eqController?.release()
        gainController?.release()
        eqController = null
        gainController = null
    }
}
