package com.focuslock.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Calm-inspired dark atmospheric theme.
 * Deep twilight gradients, soft ambient radial glow, and frosted glass surfaces.
 */
object ThemeState {
    private const val DEFAULT_ACCENT = 0xFF7DD3FC.toInt() // Serene Calm Sky-Aqua

    var accent by mutableStateOf(Color(DEFAULT_ACCENT)); private set
    var background by mutableStateOf<ImageBitmap?>(null); private set
    var dim by mutableStateOf(0.58f); private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private fun file(ctx: Context) = File(ctx.filesDir, "bg.jpg")

    fun load(ctx: Context) {
        accent = Color(prefs(ctx).getInt("accent", DEFAULT_ACCENT))
        dim = prefs(ctx).getFloat("dim", 0.58f)
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

// Calm-inspired twilight palette (Dark-only)
val Bg = Color(0xFF090D1A)
val BgMid = Color(0xFF10182E)
val BgBottom = Color(0xFF070A14)
val Fg = Color(0xFFF4F6FB)
val Muted = Color(0xFF9BA3B8)
val Line = Color(0xFF18223A)
val GlassSurface = Color(0xFF162038).copy(alpha = 0.72f)
val GlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.09f)
val Accent: Color get() = ThemeState.accent
val Danger = Color(0xFFFF7B7B)

val SerifFont = FontFamily.Serif

data class AccentPreset(val name: String, val color: Color)

val ACCENT_PRESETS_NAMED = listOf(
    AccentPreset("Calm Sky", Color(0xFF7DD3FC)),
    AccentPreset("Twilight Lavender", Color(0xFFA78BFA)),
    AccentPreset("Aurora Mint", Color(0xFF6EE7B7)),
    AccentPreset("Warm Sunset", Color(0xFFFBBF77)),
    AccentPreset("Ocean Indigo", Color(0xFF818CF8)),
    AccentPreset("Rose Mist", Color(0xFFF9A8D4)),
    AccentPreset("Northern Teal", Color(0xFF5EEAD4)),
    AccentPreset("Moonlight", Color(0xFFE2E8F0))
)

val ACCENT_PRESETS = ACCENT_PRESETS_NAMED.map { it.color }

@Composable
fun ThemedBackground(content: @Composable () -> Unit) {
    val curAccent = Accent
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Bg, BgMid, BgBottom))
        )
    ) {
        // Ambient radial aurora glow in the user's chosen accent color
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(curAccent.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.28f),
                    radius = size.minDimension * 0.72f
                ),
                radius = size.minDimension * 0.72f,
                center = Offset(size.width * 0.5f, size.height * 0.28f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(curAccent.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.82f),
                    radius = size.minDimension * 0.55f
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.85f, size.height * 0.82f)
            )
        }

        ThemeState.background?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color(0xFF060913).copy(alpha = ThemeState.dim)))
        }
        content()
    }
}

/** Frosted glassmorphic card used across Calm-style screens. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    borderColor: Color = GlassBorder,
    padding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val base = modifier
        .clip(shape)
        .background(GlassSurface)
        .border(1.dp, borderColor, shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(padding)
    Column(modifier = base, content = content)
}

/** Primary glowing pill CTA button. */
@Composable
fun PrimaryPillButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Accent,
    textColor: Color = Color(0xFF080C18),
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(color, color.copy(alpha = 0.82f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

/** Subtle glass pill button for secondary actions. */
@Composable
fun SecondaryPillButton(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Fg,
    borderColor: Color = GlassBorder,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF18233C).copy(alpha = 0.78f))
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
