package com.focuslock.app

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/** UI-side handle: one play/pause toggle. No track selection by design. */
class MusicController(private val context: Context) {
    val hasMusic: Boolean = musicLibrary(context).isNotEmpty()
    var isPlaying by mutableStateOf(false)
        private set

    private var controller: MediaController? = null
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
    }

    private fun ensureMediaUnmuted() {
        try {
            val am = context.getSystemService(AudioManager::class.java) ?: return
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (cur == 0 && max > 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (max / 3).coerceAtLeast(1), 0)
            }
        } catch (_: Exception) {}
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let(block)?.also { return }
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                c.addListener(listener)
                controller = c
                block(c)
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(context))
    }

    fun toggle() {
        if (!hasMusic) return
        withController {
            if (it.isPlaying) {
                it.pause()
            } else {
                ensureMediaUnmuted()
                if (it.playbackState == Player.STATE_IDLE || it.playbackState == Player.STATE_ENDED) {
                    it.prepare()
                }
                it.volume = 1f
                it.play()
            }
        }
    }

    fun stop() { controller?.pause() }

    fun release() {
        controller?.run { removeListener(listener); release() }
        controller = null
    }
}
