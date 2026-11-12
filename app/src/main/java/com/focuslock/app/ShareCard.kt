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
        val w = 1080
        val h = 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val accentInt = ThemeState.accent.toArgb()

        // Deep twilight background gradient
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(0xFF080C18.toInt(), 0xFF121C38.toInt(), 0xFF060912.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        // Ambient radial aurora glow
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w / 2f, h * 0.44f, 560f,
                (accentInt and 0x00FFFFFF) or 0x42000000,
                0x00000000,
                Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(w / 2f, h * 0.44f, 560f, glow)

        // Outer glass card container
        val cardRect = RectF(96f, 310f, w - 96f, h - 310f)
        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCC11192E.toInt()
            style = Paint.Style.FILL
        }
        val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = (accentInt and 0x00FFFFFF) or 0x55000000
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        c.drawRoundRect(cardRect, 72f, 72f, cardFill)
        c.drawRoundRect(cardRect, 72f, 72f, cardBorder)

        // Circular halo ring inside the card
        val ringCenterY = 790f
        val ringRadius = 210f
        val ringTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        val ringAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentInt
            style = Paint.Style.STROKE
            strokeWidth = 12f
            strokeCap = Paint.Cap.ROUND
        }
        c.drawCircle(w / 2f, ringCenterY, ringRadius, ringTrack)
        val arcRect = RectF(
            w / 2f - ringRadius,
            ringCenterY - ringRadius,
            w / 2f + ringRadius,
            ringCenterY + ringRadius
        )
        c.drawArc(arcRect, -90f, 290f, false, ringAccent)

        fun paint(
            size: Float,
            bold: Boolean,
            color: Int = Color.WHITE,
            alpha: Int = 255,
            serif: Boolean = false
        ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

        // Top label pill inside card
        c.drawText(label.uppercase(), w / 2f, 460f, paint(34f, true, accentInt, 240))

        // Headline inside the circular halo
        val headlineLines = wrap(headline, paint(96f, false, Color.WHITE, 255, serif = true), 360f).take(2)
        if (headlineLines.size == 1) {
            c.drawText(headlineLines[0], w / 2f, ringCenterY + 34f, paint(102f, false, Color.WHITE, 255, serif = true))
        } else {
            headlineLines.forEachIndexed { i, line ->
                c.drawText(line, w / 2f, ringCenterY - 12f + i * 84f, paint(72f, false, Color.WHITE, 255, serif = true))
            }
        }

        // Subtitle below halo
        val subP = paint(42f, false, Color.WHITE, 215)
        wrap(sub, subP, w - 300f).take(3).forEachIndexed { i, line ->
            c.drawText(line, w / 2f, 1140f + i * 62f, subP)
        }

        // Divider line inside card
        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x24FFFFFF
            strokeWidth = 2f
        }
        c.drawLine(220f, 1380f, w - 220f, 1380f, divPaint)

        // Brand footer inside card
        c.drawText("FocusLock", w / 2f, 1475f, paint(54f, false, Color.WHITE, 255, serif = true))
        c.drawText("Find your focus.", w / 2f, 1532f, paint(32f, false, accentInt, 220))
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
