package com.focuslock.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos

private data class BreathPhase(
    val tag: String,
    val prompt: String,
    val durationSec: Int,
    val startScale: Float,
    val endScale: Float
)

private data class BreathTechnique(
    val id: String,
    val title: String,
    val ratioLabel: String,
    val description: String,
    val phases: List<BreathPhase>
)

private val BREATH_TECHNIQUES = listOf(
    BreathTechnique(
        id = "calm",
        title = "Calm Breath",
        ratioLabel = "4 · 2 · 6",
        description = "Gentle extended exhale to release study tension and lower heart rate.",
        phases = listOf(
            BreathPhase("INHALE", "Breathe in slowly", 4, 0.54f, 1.0f),
            BreathPhase("HOLD", "Hold softly", 2, 1.0f, 1.0f),
            BreathPhase("EXHALE", "Release slowly", 6, 1.0f, 0.54f)
        )
    ),
    BreathTechnique(
        id = "box",
        title = "Box Breath",
        ratioLabel = "4 · 4 · 4 · 4",
        description = "Balanced four-part rhythm to clear mental fatigue and restore clarity.",
        phases = listOf(
            BreathPhase("INHALE", "Breathe in", 4, 0.54f, 1.0f),
            BreathPhase("HOLD", "Hold full", 4, 1.0f, 1.0f),
            BreathPhase("EXHALE", "Breathe out", 4, 1.0f, 0.54f),
            BreathPhase("REST", "Rest empty", 4, 0.54f, 0.54f)
        )
    ),
    BreathTechnique(
        id = "relax",
        title = "4-7-8 Wind Down",
        ratioLabel = "4 · 7 · 8",
        description = "Deep restorative pattern for post-study recovery and evening calm.",
        phases = listOf(
            BreathPhase("INHALE", "Inhale through nose", 4, 0.54f, 1.0f),
            BreathPhase("HOLD", "Hold deeply", 7, 1.0f, 1.0f),
            BreathPhase("EXHALE", "Long slow exhale", 8, 1.0f, 0.54f)
        )
    )
)

/**
 * Optional post-session breathing & wind-down screen.
 * The phone is already unlocked and Do Not Disturb is restored before this screen is shown,
 * so the user can either enjoy 1–3 guided breaths or tap Skip / Done at any time.
 */
@Composable
fun BreathingScreen(
    completedMinutes: Int,
    music: MusicController,
    onDismiss: () -> Unit
) {
    BackHandler {
        music.stop()
        onDismiss()
    }

    var selectedIdx by remember { mutableStateOf(0) }
    val technique = BREATH_TECHNIQUES[selectedIdx]

    var phaseIndex by remember(selectedIdx) { mutableStateOf(0) }
    var phaseElapsedMs by remember(selectedIdx) { mutableStateOf(0L) }
    var completedCycles by remember(selectedIdx) { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }

    val currentPhase = technique.phases[phaseIndex.coerceIn(0, technique.phases.lastIndex)]
    val phaseDurationMs = currentPhase.durationSec * 1000L

    LaunchedEffect(selectedIdx, phaseIndex, isPaused) {
        val tickMs = 32L
        while (!isPaused) {
            delay(tickMs)
            val next = phaseElapsedMs + tickMs
            if (next >= phaseDurationMs) {
                phaseElapsedMs = 0L
                if (phaseIndex + 1 >= technique.phases.size) {
                    phaseIndex = 0
                    completedCycles += 1
                } else {
                    phaseIndex += 1
                }
            } else {
                phaseElapsedMs = next
            }
        }
    }

    val rawProgress = (phaseElapsedMs.toFloat() / phaseDurationMs.toFloat()).coerceIn(0f, 1f)
    // Smooth cosine ease-in-out interpolation
    val eased = (0.5f - 0.5f * cos(PI * rawProgress)).toFloat()
    val orbScale = currentPhase.startScale + (currentPhase.endScale - currentPhase.startScale) * eased
    val secondsLeft = ((phaseDurationMs - phaseElapsedMs + 999L) / 1000L).toInt().coerceAtLeast(1)

    val totalCycleMs = technique.phases.sumOf { it.durationSec * 1000L }.coerceAtLeast(1L)
    val completedPhasesMs = technique.phases.take(phaseIndex).sumOf { it.durationSec * 1000L } + phaseElapsedMs
    val cycleProgress = (completedPhasesMs.toFloat() / totalCycleMs.toFloat()).coerceIn(0f, 1f)

    val curAccent = Accent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header with Skip button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(curAccent.copy(alpha = 0.14f))
                    .border(1.dp, curAccent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (completedMinutes > 0) "✓  SESSION COMPLETE · ${completedMinutes}M" else "◯  MINDFUL WIND-DOWN",
                    color = curAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassSurface)
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .clickable {
                        music.stop()
                        onDismiss()
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text("Skip  ✕", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Take a deep breath.",
            fontFamily = SerifFont,
            fontSize = 30.sp,
            color = Fg,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (completedMinutes > 0) {
                "Your phone is unlocked. Pause for 3 slow breaths before jumping back in."
            } else {
                "Follow the expanding halo to calm your mind and reset your focus."
            },
            color = Muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(Modifier.height(16.dp))

        // Technique Selector Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BREATH_TECHNIQUES.forEachIndexed { idx, item ->
                val selected = idx == selectedIdx
                val shape = RoundedCornerShape(18.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(if (selected) curAccent.copy(alpha = 0.2f) else GlassSurface)
                        .border(
                            1.dp,
                            if (selected) curAccent else GlassBorder,
                            shape
                        )
                        .clickable { selectedIdx = idx }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.title,
                        color = if (selected) Fg else Muted,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.ratioLabel,
                        color = if (selected) curAccent else Muted.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // Animated Breathing Orb
        Box(
            modifier = Modifier
                .size(276.dp)
                .clip(CircleShape)
                .clickable { isPaused = !isPaused },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension * 0.44f
                val orbR = maxR * orbScale

                // Outer soft ambient bloom
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            curAccent.copy(alpha = 0.28f * orbScale),
                            curAccent.copy(alpha = 0.08f * orbScale),
                            Color.Transparent
                        ),
                        center = center,
                        radius = maxR * 1.15f
                    ),
                    radius = maxR * 1.15f,
                    center = center
                )

                // Middle translucent petal ring
                drawCircle(
                    color = curAccent.copy(alpha = 0.14f),
                    radius = (orbR * 1.14f).coerceAtMost(maxR * 1.06f),
                    center = center
                )

                // Core breathing sphere
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            curAccent.copy(alpha = 0.36f),
                            curAccent.copy(alpha = 0.16f),
                            Color(0xFF10182E).copy(alpha = 0.85f)
                        ),
                        center = center,
                        radius = orbR
                    ),
                    radius = orbR,
                    center = center
                )

                // Outer guide track ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = maxR,
                    center = center,
                    style = Stroke(width = 4.dp.toPx())
                )

                // Cycle progress arc around the outer ring
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(curAccent.copy(alpha = 0.35f), curAccent, curAccent)
                    ),
                    startAngle = -90f,
                    sweepAngle = cycleProgress * 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - maxR, center.y - maxR),
                    size = androidx.compose.ui.geometry.Size(maxR * 2f, maxR * 2f),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isPaused) "PAUSED" else currentPhase.tag,
                    color = curAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isPaused) "Tap to resume" else currentPhase.prompt,
                    fontFamily = SerifFont,
                    color = Fg,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${secondsLeft}s",
                    color = Fg.copy(alpha = 0.9f),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Cycle progress & ambient sound row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassCard(
                modifier = Modifier.weight(1f),
                padding = 12.dp,
                cornerRadius = 18.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("BREATH CYCLES", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (completedCycles >= 3) "✓ $completedCycles completed" else "$completedCycles of 3 breaths",
                        color = if (completedCycles >= 3) curAccent else Fg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            GlassCard(
                modifier = Modifier.weight(1f),
                padding = 12.dp,
                cornerRadius = 18.dp,
                borderColor = if (music.isPlaying) curAccent.copy(alpha = 0.45f) else GlassBorder,
                onClick = { music.toggle() }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("AMBIENT SOUND", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (music.isPlaying) "♫  Playing" else "♫  Tap to Play",
                        color = if (music.isPlaying) curAccent else Fg,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = technique.description,
            color = Muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(18.dp))

        PrimaryPillButton(
            text = if (completedCycles >= 1) "Finish Wind-Down" else "Done — Return Home",
            onClick = {
                music.stop()
                onDismiss()
            }
        )

        Spacer(Modifier.height(8.dp))
    }
}
