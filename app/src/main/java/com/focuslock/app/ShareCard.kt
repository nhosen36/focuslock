package com.focuslock.app

import android.content.Context
import android.content.Intent
import android.graphics.*
import androidx.core.content.FileProvider
import java.io.File

/** Renders a 1080x1920 story-sized card and opens the system share sheet. */
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
            putExtra(Intent.EXTRA_TEXT, "$headline. $sub #FocusLock")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share your focus").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun render(label: String, headline: String, sub: String): Bitmap {
        val w = 1080; val h = 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF1B1D3A.toInt(), 0xFF4B3FD1.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        fun paint(size: Float, bold: Boolean, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; this.alpha = alpha; textSize = size; textAlign = Paint.Align.CENTER
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        c.drawText("★", w / 2f, 640f, paint(220f, true))
        c.drawText(label.uppercase(), w / 2f, 800f, paint(48f, false, 190))
        // Wrap the headline to at most two lines.
        val big = paint(92f, true)
        wrap(headline, big, w - 160f).take(3).forEachIndexed { i, line -> c.drawText(line, w / 2f, 940f + i * 110f, big) }
        val subP = paint(52f, false, 220)
        wrap(sub, subP, w - 200f).take(3).forEachIndexed { i, line -> c.drawText(line, w / 2f, 1250f + i * 70f, subP) }
        c.drawText("FocusLock", w / 2f, 1780f, paint(56f, true))
        c.drawText("Lock in. Stay focused.", w / 2f, 1850f, paint(38f, false, 190))
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
