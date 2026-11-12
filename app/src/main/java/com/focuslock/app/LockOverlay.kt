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
 * System-level full-screen lock overlay (TYPE_APPLICATION_OVERLAY).
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

    /**
     * Re-asserts the overlay at the very top of WindowManager when OEM ROMs (like Honor MagicOS)
     * try to hide floating windows during a Home swipe gesture.
     */
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

        val bgInt = Color.parseColor("#0B0B0D")
        val cardInt = Color.parseColor("#16161B")
        val fgInt = Color.parseColor("#F2F2F7")
        val mutedInt = Color.parseColor("#8E8E9A")
        val dangerInt = Color.parseColor("#F87171")
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
            setBackgroundColor(bgInt)
            isFocusable = true
            isFocusableInTouchMode = true
            applyImmersiveFlags(this)
            @Suppress("DEPRECATION")
            setOnSystemUiVisibilityChangeListener {
                // Immediately re-hide the gesture/nav bar on the 1st swipe so a 2nd swipe cannot trigger Home.
                handler.postDelayed({ applyImmersiveFlags(this) }, 30L)
            }
        }

        // --- container 1: Timer Screen ---
        val timerContainer = LinearLayout(appCtx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(48), dp(24), dp(36))
        }

        val topSpacer = View(appCtx)
        timerContainer.addView(topSpacer, LinearLayout.LayoutParams(1, 0, 1f))

        val timerText = TextView(appCtx).apply {
            setTextColor(fgInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 86f)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            gravity = Gravity.CENTER
            text = "25:00"
        }
        timerContainer.addView(timerText)

        val statusText = TextView(appCtx).apply {
            setTextColor(mutedInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            text = "screen locked  •  notifications silenced"
        }
        timerContainer.addView(statusText)

        val musicBtn = TextView(appCtx).apply {
            setTextColor(fgInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(cardInt)
                setStroke(dp(1), accentInt)
            }
            text = if (music.isPlaying) "❚❚" else "▶"
            visibility = if (music.hasMusic) View.VISIBLE else View.GONE
            setOnClickListener {
                music.toggle()
                postDelayed({ text = if (music.isPlaying) "❚❚" else "▶" }, 150)
            }
        }
        timerContainer.addView(
            musicBtn,
            LinearLayout.LayoutParams(dp(78), dp(78)).apply { topMargin = dp(38) }
        )

        val bottomSpacer = View(appCtx)
        timerContainer.addView(bottomSpacer, LinearLayout.LayoutParams(1, 0, 1f))

        val endEarlyBtn = TextView(appCtx).apply {
            setTextColor(accentInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            text = "End early (solve 5 math problems)"
        }
        timerContainer.addView(endEarlyBtn)

        val emergencyNum = emergencyNumber()
        val emergencyBtn = TextView(appCtx).apply {
            setTextColor(dangerInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            text = "Emergency call ($emergencyNum)"
        }
        timerContainer.addView(emergencyBtn)

        // --- container 2: Math Challenge Screen (with built-in keypad) ---
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
            setTextColor(mutedInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
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
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(cardInt)
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
            progressText.text = "${probIndex + 1} of $totalProblems"
            problemText.text = "$numA × $numB"
            answerBox.text = if (typedAnswer.isEmpty()) " " else typedAnswer
            if (wrong) {
                hintText.setTextColor(dangerInt)
                hintText.text = "Wrong. Starting over from 1 of $totalProblems."
            } else {
                hintText.setTextColor(mutedInt)
                hintText.text = "Solve all $totalProblems to unlock early."
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
                setTextColor(if (k == "✓") bgInt else fgInt)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (k == "✓") accentInt else cardInt)
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
                width = dp(78)
                height = dp(58)
                setMargins(dp(6), dp(6), dp(6), dp(6))
            }
            grid.addView(keyBtn, lp)
        }
        challengeContainer.addView(grid)
        challengeContainer.addView(View(appCtx), LinearLayout.LayoutParams(1, 0, 1f))

        val backToFocusBtn = TextView(appCtx).apply {
            setTextColor(mutedInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            text = "Back to focus"
            setOnClickListener {
                challengeContainer.visibility = View.GONE
                timerContainer.visibility = View.VISIBLE
            }
        }
        challengeContainer.addView(backToFocusBtn)

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
            gravity = Gravity.CENTER
            text = "Emergency Call ($emergencyNum)"
        }
        val emBody = TextView(appCtx).apply {
            setTextColor(fgInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(28))
            text = "Do you need to dial emergency services ($emergencyNum)?\n\nFor normal phone use, tap Cancel and solve the 5 math problems in 'End early'."
        }
        val dialNowBtn = TextView(appCtx).apply {
            setTextColor(bgInt)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(dangerInt)
            }
            setPadding(dp(28), dp(14), dp(28), dp(14))
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(16), dp(20), dp(16))
            text = "Cancel (Back to focus)"
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
                musicBtn.text = if (music.isPlaying) "❚❚" else "▶"
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
