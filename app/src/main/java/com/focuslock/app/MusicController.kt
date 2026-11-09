package com.focuslock.app

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

/** UI-side handle: one play/pause toggle. No track selection by design. */
class MusicController(private val context: Context) {
    val hasMusic: Boolean = musicLibrary(context).isNotEmpty()
    var isPlaying by mutableStateOf(false)
        private set

    private var controller: MediaController? = null
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let(block)?.also { return }
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = future.get()
            c.addListener(listener)
            controller = c
            block(c)
        }, ContextCompat.getMainExecutor(context))
    }

    fun toggle() {
        if (!hasMusic) return
        withController { if (it.isPlaying) it.pause() else it.play() }
    }

    fun stop() { controller?.pause() }

    fun release() {
        controller?.run { removeListener(listener); release() }
        controller = null
    }
}
