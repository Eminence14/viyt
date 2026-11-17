package com.viyt.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.viyt.app.ui.MainActivity

/** Keeps playback going with the screen off, with lock-screen and notification controls. */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_EXPAND, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, PlayerHolder.get(this))
            .setSessionActivity(open)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /**
     * The user swiped viyt away from recent apps: that means "close", so stop all playback,
     * remove the notification and let the app shut down. (Minimising never reaches here.)
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        PlayerHolder.shutdown()
        com.viyt.app.ui.Watch.detach()
        com.viyt.app.ui.MusicUi.nowPlaying = false
        com.viyt.app.ui.ShortsPlayer.pause()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        session?.run { release() }
        session = null
        super.onDestroy()
    }
}
