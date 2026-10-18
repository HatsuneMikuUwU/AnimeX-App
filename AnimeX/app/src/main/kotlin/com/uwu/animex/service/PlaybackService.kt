package com.uwu.animex.service

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

object PlaybackSessions {
    @Volatile
    var current: MediaSession? = null
}

class PlaybackService : MediaSessionService() {
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = PlaybackSessions.current
}
