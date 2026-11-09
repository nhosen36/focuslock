package com.focuslock.app

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

private val AUDIO_EXT = setOf("mp3", "ogg", "m4a", "wav", "aac")

/** Our own licensed tracks, bundled in app/src/main/assets/music/. */
fun musicLibrary(context: Context): List<String> =
    (context.assets.list("music") ?: emptyArray())
        .filter { it.substringAfterLast('.', "").lowercase() in AUDIO_EXT }

/** Plays random tracks from the bundled library, with only play/pause exposed. */
@androidx.annotation.OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        exo.setMediaItems(musicLibrary(this).map { MediaItem.fromUri("asset:///music/$it") })
        exo.shuffleModeEnabled = true
        exo.repeatMode = Player.REPEAT_MODE_ALL
        exo.prepare()

        // Hide skip/seek everywhere (app UI, notification, lock screen, headset): play/pause only.
        val playPauseOnly = object : ForwardingPlayer(exo) {
            override fun getAvailableCommands(): Player.Commands =
                super.getAvailableCommands().buildUpon().removeAll(
                    COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_PREVIOUS,
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    COMMAND_SEEK_TO_MEDIA_ITEM, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    COMMAND_SEEK_BACK, COMMAND_SEEK_FORWARD,
                    COMMAND_SET_SHUFFLE_MODE, COMMAND_SET_REPEAT_MODE,
                    COMMAND_CHANGE_MEDIA_ITEMS
                ).build()

            override fun isCommandAvailable(command: Int): Boolean =
                availableCommands.contains(command)
        }
        session = MediaSession.Builder(this, playPauseOnly).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
