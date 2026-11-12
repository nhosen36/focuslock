package com.focuslock.app

import android.content.Context
import android.content.Intent
import android.graphics.*
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import java.io.File

/** Renders a Calm-styled 1080x1920 story-sized card and opens the system share sheet. */
object ShareCard {
    fun share(ctx: Context, label: String, headline: String, sub: String) {
        val bmp = render(label, headline, sub)
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "focuslock_card.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "$headline — $sub #FocusLock")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share your focus").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun render(label: String, headline: String, sub: String): Bitmap {
        val w = 1080; val h = 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val accentInt = ThemeState.accent.toArgb()

        val bg = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(0xFF090D1A.toInt(), 0xFF131F3E.toInt(), 0xFF070A14.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        // Ambient radial glow
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w / 2f, h * 0.42f, 520f,
                (accentInt and 0x00FFFFFF) or 0x44000000,
                0x00000000,
                Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(w / 2f, h * 0.42f, 520f, glow)

        fun paint(size: Float, bold: Boolean, color: Int = Color.WHITE, alpha: Int = 255, serif: Boolean = false) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                this.alpha = alpha
                textSize = size
                textAlign = Paint.Align.CENTER
                typeface = when {
                    serif -> Typeface.create("serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
                    bold -> Typeface.DEFAULT_BOLD
                    else -> Typeface.DEFAULT
                }
            }

        c.drawText("✦", w / 2f, 620f, paint(180f, true, accentInt))
        c.drawText(label.uppercase(), w / 2f, 770f, paint(40f, true, accentInt, 230))
        val big = paint(108f, false, Color.WHITE, 255, serif = true)
        wrap(headline, big, w - 160f).take(3).forEachIndexed { i, line ->
            c.drawText(line, w / 2f, 920f + i * 120f, big)
        }
        val subP = paint(48f, false, Color.WHITE, 200)
        wrap(sub, subP, w - 200f).take(3).forEachIndexed { i, line ->
            c.drawText(line, w / 2f, 1240f + i * 68f, subP)
        }
        c.drawText("FocusLock", w / 2f, 1750f, paint(60f, false, Color.WHITE, 255, serif = true))
        c.drawText("Find your focus.", w / 2f, 1820f, paint(36f, false, accentInt, 210))
        return bmp
    }

    private fun wrap(text: String, p: Paint, maxW: Float): List<String> {
        val lines = mutableListOf<String>(); var cur = ""
        for (word in text.split(' ')) {
            val t = if (cur.isEmpty()) word else "$cur $word"
            if (p.measureText(t) > maxW && cur.isNotEmpty()) { lines += cur; cur = word } else cur = t
        }
        if (cur.isNotEmpty()) lines += cur
        return lines
    }
}
