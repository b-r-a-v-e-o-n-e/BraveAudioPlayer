package org.widelogic.braveaudio.playback

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Media3 supplies media-style playback controls for notification, headphones and lock screen.
 * Sleep timer is owned by service, not by an Activity/composable lifecycle.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timerTask: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlaybackHolder.init(this)
        session = MediaSession.Builder(this, player).build()
        val wallDeadline = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_DEADLINE, 0L)
        if (wallDeadline > System.currentTimeMillis()) schedule(wallDeadline)
        else clearDeadline()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SLEEP -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 0).coerceIn(0, 240)
                if (minutes == 0) cancelTimer()
                else schedule(System.currentTimeMillis() + minutes * 60_000L)
            }
            ACTION_CANCEL_SLEEP -> cancelTimer()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    private fun schedule(deadlineWallTime: Long) {
        timerTask?.let(handler::removeCallbacks)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putLong(KEY_DEADLINE, deadlineWallTime).apply()
        val delay = (deadlineWallTime - System.currentTimeMillis()).coerceAtLeast(0L)
        val task = Runnable {
            timerTask = null
            runCatching { PlaybackHolder.player.pause() }
            clearDeadline()
        }
        timerTask = task
        handler.postAtTime(task, SystemClock.uptimeMillis() + delay)
    }

    private fun clearDeadline() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_DEADLINE).apply()
    }

    private fun cancelTimer() {
        timerTask?.let(handler::removeCallbacks)
        timerTask = null
        clearDeadline()
    }

    override fun onDestroy() {
        timerTask?.let(handler::removeCallbacks)
        timerTask = null
        session?.release()
        session = null
        AudioEffects.release()
        // Do not release the process-wide player here: an Activity may still hold
        // its instance if Android recycles only the Service. The OS reclaims it
        // when the application process terminates.
        super.onDestroy()
    }

    companion object {
        const val ACTION_SLEEP = "org.widelogic.braveaudio.SLEEP_TIMER"
        const val ACTION_CANCEL_SLEEP = "org.widelogic.braveaudio.SLEEP_CANCEL"
        const val EXTRA_MINUTES = "minutes"
        const val PREFS = "brave_audio_timer"
        const val KEY_DEADLINE = "deadline_epoch_ms"
    }
}
