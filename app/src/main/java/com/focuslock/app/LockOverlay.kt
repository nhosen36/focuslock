package com.focuslock.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import kotlin.random.Random

/**
 * Calm-styled system-level full-screen lock overlay (TYPE_APPLICATION_OVERLAY).
 * Stays on top even if the user swipes the Home gesture twice or presses Recents,
 * and removes itself automatically the instant the session ends or the 5 math problems are solved.
 */
object LockOverlay {
    private var rootView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    val isShowing: Boolean get() = rootView != null

    @Suppress("DEPRECATION")
    private fun applyImmersiveFlags(v: View) {
        try {
            v.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        } catch (_: Exception) {}
    }

    fun reassertOnTop(context: Context) {
        val appCtx = context.applicationContext
        val wm = appCtx.getSystemService(WindowManager::class.java) ?: return
        val v = rootView ?: return
        val lp = layoutParams ?: return
        val delays = longArrayOf(0L, 100L, 300L, 650L)
        for (d in delays) {
            handler.postDelayed({
                if (rootView === v) {
                    try {
                        v.visibility = View.VISIBLE
                        applyImmersiveFlags(v)
                        try {
                            wm.removeViewImmediate(v)
                        } catch (_: Exception) {}
                        wm.addView(v, lp)
                        v.requestFocus()
                    } catch (_: Exception) {
                        try {
                            wm.updateViewLayout(v, lp)
                        } catch (_: Exception) {}
                    }
                }
            }, d)
        }
    }

    fun show(
        context: Context,
        store: SessionStore,
        music: MusicController,
        onSessionFinished: () -> Unit
    ) {
        val appCtx = context.applicationContext
        if (!Settings.canDrawOverlays(appCtx)) return
        if (rootView != null) return

        val wm = appCtx.getSystemService(WindowManager::class.java) ?: return
        val density = appCtx.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val bgTop = Color.parseColor("#090D1A")
        val bgMid = Color.parseColor("#111B34")
        val bgBot = Color.parseColor("#070A14")
        val glassInt = Color.parseColor("#CC162038")
        val borderSubtle = Color.parseColor("#26FFFFFF")
        val fgInt = Color.parseColor("#F4F6FB")
        val mutedInt = Color.parseColor("#9BA3B8")
        val dangerInt = Color.parseColor("#FF7B7B")
        val accentInt = ThemeState.accent.toArgb()

        val root = object : FrameLayout(appCtx) {
            override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
                if (event?.keyCode == KeyEvent.KEYCODE_BACK ||
                    event?.keyCode == KeyEvent.KEYCODE_HOME ||
                    event?.keyCode == KeyEvent.KEYCODE_APP_SWITCH
                ) {
                    return true
                }
                return super.dispatchKeyEvent(event)
            }

            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                applyImmersiveFlags(this)
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                super.onLayout(changed, left, top, right, bottom)
                if (Build.VERSION.SDK_INT >= 29 && right > left && bottom > top) {
                    try {
                        systemGestureExclusionRects = listOf(
                            Rect(0, bottom - dp(120), right, bottom),
                            Rect(0, 0, right, dp(80))
                        )
                    } catch (_: Exception) {}
                }
            }
        }.apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(bgTop, bgMid, bgBot)
            )
            isFocusable = true
            isFocusableInTouchMode = true
            applyImmersiveFlags(this)
            @Suppress("DEPRECATION")
            setOnSystemUiVisibilityChangeListener {
                handler.postDelayed({ applyImmersiveFlags(this) }, 30L)
            }
        }

        // --- container 1: Calm Timer Screen ---
        val timerContainer = LinearLayout(appCtx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(52), dp(24), dp(36))
        }

        val topTag = TextView(appCtx).apply {
            setTextColor(accentInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.18f
            text = "DEEP FOCUS IN PROGRESS"
        }
        timerContainer.addView(topTag)

        val topSubtitle = TextView(appCtx).apply {
            setTextColor(mutedInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("serif", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
            text = "Stay present. Distractions are silenced."
        }
        timerContainer.addView(topSubtitle)

        timerContainer.addView(View(appCtx), LinearLayout.LayoutParams(1, 0, 1f))

        // Circular halo dial around the countdown
        val dialBox = LinearLayout(appCtx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33162038"))
                setStroke(dp(3), accentInt)
            }
        }
        val timerText = TextView(appCtx).apply {
            setTextColor(fgInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 68f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
            text = "25:00"
        }
        val remLabel = TextView(appCtx).apply {
            setTextColor(mutedInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.2f
            gravity = Gravity.CENTER
            text = "REMAINING"
        }
        dialBox.addView(timerText)
        dialBox.addView(remLabel)
        timerContainer.addView(
            dialBox,
            LinearLayout.LayoutParams(dp(256), dp(256))
        )

        fun musicButtonText(playing: Boolean) =
            if (playing) "❚❚   Pause Ambient Sound" else "▶   Play Ambient Sound"

        val musicBtn = TextView(appCtx).apply {
            setTextColor(fgInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(13), dp(24), dp(13))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(glassInt)
                setStroke(dp(1), accentInt)
            }
            text = musicButtonText(music.isPlaying)
            visibility = if (music.hasMusic) View.VISIBLE else View.GONE
            setOnClickListener {
                music.toggle()
                postDelayed({ text = musicButtonText(music.isPlaying) }, 150)
            }
        }
        timerContainer.addView(
            musicBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(28) }
        )

        timerContainer.addView(View(appCtx), LinearLayout.LayoutParams(1, 0, 1f))

        val endEarlyBtn = TextView(appCtx).apply {
            setTextColor(fgInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(13), dp(24), dp(13))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(glassInt)
                setStroke(dp(1), borderSubtle)
            }
            text = "End Early (Solve 5 Math Challenges)"
        }
        timerContainer.addView(
            endEarlyBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val emergencyNum = emergencyNumber()
        val emergencyBtn = TextView(appCtx).apply {
            setTextColor(dangerInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(8))
            text = "Emergency Call ($emergencyNum)"
        }
        timerContainer.addView(emergencyBtn)

        // --- container 2: Math Challenge Screen (with built-in glass keypad) ---
        val challengeContainer = LinearLayout(appCtx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(48), dp(24), dp(36))
            visibility = View.GONE
        }

        val totalProblems = 5
        var probIndex = 0
        var numA = Random.nextInt(12, 40)
        var numB = Random.nextInt(12, 40)
        var typedAnswer = ""

        val progressText = TextView(appCtx).apply {
            setTextColor(accentInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.15f
            gravity = Gravity.CENTER
        }
        val problemText = TextView(appCtx).apply {
            setTextColor(fgInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }
        val answerBox = TextView(appCtx).apply {
            setTextColor(fgInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(glassInt)
                setStroke(dp(1), accentInt)
            }
            setPadding(dp(24), dp(12), dp(24), dp(12))
        }
        val hintText = TextView(appCtx).apply {
            setTextColor(mutedInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(16))
        }

        fun updateChallengeLabels(wrong: Boolean = false) {
            progressText.text = "MIND CHECK  •  ${probIndex + 1} OF $totalProblems"
            problemText.text = "$numA × $numB"
            answerBox.text = if (typedAnswer.isEmpty()) " " else typedAnswer
            if (wrong) {
                hintText.setTextColor(dangerInt)
                hintText.text = "Incorrect — restarting from problem 1 of $totalProblems"
            } else {
                hintText.setTextColor(mutedInt)
                hintText.text = "Solve all $totalProblems problems to end your session early"
            }
        }
        updateChallengeLabels()

        challengeContainer.addView(View(appCtx), LinearLayout.LayoutParams(1, 0, 1f))
        challengeContainer.addView(progressText)
        challengeContainer.addView(problemText)
        challengeContainer.addView(
            answerBox,
            LinearLayout.LayoutParams(dp(200), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            }
        )
        challengeContainer.addView(hintText)

        val grid = GridLayout(appCtx).apply {
            columnCount = 3
            rowCount = 4
        }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "✓")
        for (k in keys) {
            val keyBtn = TextView(appCtx).apply {
                text = k
                setTextColor(if (k == "✓") bgTop else fgInt)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(if (k == "✓") accentInt else glassInt)
                    setStroke(dp(1), if (k == "✓") accentInt else borderSubtle)
                }
                setOnClickListener {
                    when (k) {
                        "⌫" -> {
                            if (typedAnswer.isNotEmpty()) {
                                typedAnswer = typedAnswer.dropLast(1)
                                updateChallengeLabels(false)
                            }
                        }
                        "✓" -> {
                            if (typedAnswer.toIntOrNull() == numA * numB) {
                                if (probIndex + 1 >= totalProblems) {
                                    hide(appCtx)
                                    onSessionFinished()
                                } else {
                                    probIndex++
                                    numA = Random.nextInt(12, 40)
                                    numB = Random.nextInt(12, 40)
                                    typedAnswer = ""
                                    updateChallengeLabels(false)
                                }
                            } else {
                                probIndex = 0
                                numA = Random.nextInt(12, 40)
                                numB = Random.nextInt(12, 40)
                                typedAnswer = ""
                                updateChallengeLabels(true)
                            }
                        }
                        else -> {
                            if (typedAnswer.length < 6) {
                                typedAnswer += k
                                updateChallengeLabels(false)
                            }
                        }
                    }
                }
            }
            val lp = GridLayout.LayoutParams().apply {
                width = dp(82)
                height = dp(56)
                setMargins(dp(5), dp(5), dp(5), dp(5))
            }
            grid.addView(keyBtn, lp)
        }
        challengeContainer.addView(grid)
        challengeContainer.addView(View(appCtx), LinearLayout.LayoutParams(1, 0, 1f))

        val backToFocusBtn = TextView(appCtx).apply {
            setTextColor(fgInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp(22), dp(12), dp(22), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(glassInt)
                setStroke(dp(1), borderSubtle)
            }
            text = "Return to Focus Session"
            setOnClickListener {
                challengeContainer.visibility = View.GONE
                timerContainer.visibility = View.VISIBLE
            }
        }
        challengeContainer.addView(
            backToFocusBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // --- container 3: Emergency Call Confirmation ---
        val emergencyContainer = LinearLayout(appCtx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(48), dp(32), dp(36))
            visibility = View.GONE
        }
        val emTitle = TextView(appCtx).apply {
            setTextColor(dangerInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.create("serif", Typeface.NORMAL)
            gravity = Gravity.CENTER
            text = "Emergency Call ($emergencyNum)"
        }
        val emBody = TextView(appCtx).apply {
            setTextColor(fgInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(28))
            text = "Do you need to dial emergency services ($emergencyNum)?\n\nFor normal phone use, tap Cancel and solve the 5 math problems in 'End Early'."
        }
        val dialNowBtn = TextView(appCtx).apply {
            setTextColor(bgTop)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(dangerInt)
            }
            setPadding(dp(32), dp(14), dp(32), dp(14))
            text = "Dial $emergencyNum"
            setOnClickListener {
                hide(appCtx)
                try {
                    appCtx.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyNum"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
        }
        val cancelEmBtn = TextView(appCtx).apply {
            setTextColor(accentInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(18), dp(20), dp(16))
            text = "Cancel (Return to Focus)"
            setOnClickListener {
                emergencyContainer.visibility = View.GONE
                timerContainer.visibility = View.VISIBLE
            }
        }
        emergencyContainer.addView(emTitle)
        emergencyContainer.addView(emBody)
        emergencyContainer.addView(dialNowBtn)
        emergencyContainer.addView(cancelEmBtn)

        endEarlyBtn.setOnClickListener {
            probIndex = 0
            numA = Random.nextInt(12, 40)
            numB = Random.nextInt(12, 40)
            typedAnswer = ""
            updateChallengeLabels(false)
            timerContainer.visibility = View.GONE
            challengeContainer.visibility = View.VISIBLE
        }

        emergencyBtn.setOnClickListener {
            timerContainer.visibility = View.GONE
            emergencyContainer.visibility = View.VISIBLE
        }

        root.addView(
            timerContainer,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        root.addView(
            challengeContainer,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        root.addView(
            emergencyContainer,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )

        @Suppress("DEPRECATION")
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            wm.addView(root, lp)
            rootView = root
            layoutParams = lp
            root.requestFocus()
        } catch (_: Exception) {
            return
        }

        val r = object : Runnable {
            override fun run() {
                val curRoot = rootView ?: return
                val remMs = store.endTimeMs - System.currentTimeMillis()
                if (remMs <= 0L) {
                    hide(appCtx)
                    onSessionFinished()
                    return
                }
                store.enforceIfActive()
                applyImmersiveFlags(curRoot)
                val sec = (remMs / 1000L).coerceAtLeast(0L)
                timerText.text = "%02d:%02d".format(sec / 60, sec % 60)
                musicBtn.text = musicButtonText(music.isPlaying)
                handler.postDelayed(this, 250L)
            }
        }
        ticker = r
        handler.post(r)
    }

    fun hide(context: Context) {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
        val v = rootView ?: return
        rootView = null
        layoutParams = null
        try {
            val wm = context.applicationContext.getSystemService(WindowManager::class.java)
            wm?.removeViewImmediate(v)
        } catch (_: Exception) {}
    }
}
