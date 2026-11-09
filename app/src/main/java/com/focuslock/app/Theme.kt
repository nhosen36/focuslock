package com.focuslock.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import java.io.File

/**
 * Dark only (no light theme). The user can pick an accent colour and an optional background image.
 * The image is copied into app-private storage, so no storage permission is needed.
 */
object ThemeState {
    private const val DEFAULT_ACCENT = 0xFF8B7CFF.toInt()

    var accent by mutableStateOf(Color(DEFAULT_ACCENT)); private set
    var background by mutableStateOf<ImageBitmap?>(null); private set
    var dim by mutableStateOf(0.6f); private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun file(ctx: Context) = File(ctx.filesDir, "bg.jpg")

    fun load(ctx: Context) {
        accent = Color(prefs(ctx).getInt("accent", DEFAULT_ACCENT))
        dim = prefs(ctx).getFloat("dim", 0.6f)
        background = file(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
    }

    fun setAccent(ctx: Context, c: Color) {
        accent = c
        prefs(ctx).edit().putInt("accent", c.toArgb()).apply()
    }

    fun setDim(ctx: Context, d: Float) {
        dim = d
        prefs(ctx).edit().putFloat("dim", d).apply()
    }

    fun setBackground(ctx: Context, uri: Uri) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1440) sample *= 2
            val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return
            file(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            background = bmp.asImageBitmap()
        } catch (_: Exception) {
        }
    }

    fun clearBackground(ctx: Context) {
        file(ctx).delete()
        background = null
    }
}

// Palette read by every screen. Reading Accent inside a composable recomposes when it changes.
val Bg = Color(0xFF0B0B0D)
val Fg = Color(0xFFF2F2F3)
val Muted = Color(0xFF9A9AA0)
val Line = Color(0xFF26262B)
val Accent: Color get() = ThemeState.accent
val Danger = Color(0xFFFF6B6B)

val ACCENT_PRESETS = listOf(
    Color(0xFF8B7CFF), Color(0xFFF4C27A), Color(0xFF6EE7A8), Color(0xFF6CB6FF),
    Color(0xFFFF8FB1), Color(0xFFFF9F5A), Color(0xFF4FD1C5), Color(0xFFF2F2F3)
)

@Composable
fun ThemedBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        ThemeState.background?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            // Scrim keeps text readable on any photo.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = ThemeState.dim)))
        }
        content()
    }
}
