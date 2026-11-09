package com.focuslock.app

import android.content.Context
import java.time.LocalDate

data class Award(val hours: Int, val title: String)

val HOUR_AWARDS = listOf(
    Award(1, "First Hour"),
    Award(10, "Getting Serious"),
    Award(25, "Deep Worker"),
    Award(50, "Focus Machine"),
    Award(100, "Century Club"),
    Award(250, "Master of Focus"),
    Award(500, "Unshakeable"),
    Award(1000, "Legend")
)

val STREAK_AWARDS = listOf(
    Award(3, "3-Day Streak"),
    Award(7, "Week Warrior"),
    Award(30, "Month of Focus"),
    Award(100, "100-Day Streak")
)

/** Daily focus totals, stored locally on the device only. Format: "day,minutes,sessions;..." */
class StatsStore(context: Context) {
    private val prefs = context.getSharedPreferences("stats", Context.MODE_PRIVATE)

    data class Day(val epochDay: Long, val minutes: Int, val sessions: Int)

    private fun load(): MutableMap<Long, Day> {
        val out = mutableMapOf<Long, Day>()
        prefs.getString("log", "")!!.split(';').filter { it.isNotBlank() }.forEach {
            val p = it.split(',')
            if (p.size == 3) out[p[0].toLong()] = Day(p[0].toLong(), p[1].toInt(), p[2].toInt())
        }
        return out
    }

    private fun save(m: Map<Long, Day>) {
        prefs.edit().putString("log", m.values.joinToString(";") { "${it.epochDay},${it.minutes},${it.sessions}" }).apply()
    }

    fun addSession(minutes: Int) {
        if (minutes < 1) return
        val today = LocalDate.now().toEpochDay()
        val m = load()
        val d = m[today] ?: Day(today, 0, 0)
        m[today] = Day(today, d.minutes + minutes, d.sessions + 1)
        save(m)
    }

    fun totalMinutes(): Int = load().values.sumOf { it.minutes }

    /** Focused minutes in the current calendar month. Used for the free monthly allowance. */
    fun minutesThisMonth(): Int {
        val now = LocalDate.now()
        return load().values.filter {
            val d = LocalDate.ofEpochDay(it.epochDay)
            d.year == now.year && d.month == now.month
        }.sumOf { it.minutes }
    }

    /** Oldest to newest, one entry per day for the last [days] days, including empty days. */
    fun lastDays(days: Int): List<Day> {
        val m = load()
        val today = LocalDate.now().toEpochDay()
        return (days - 1 downTo 0).map { off -> m[today - off] ?: Day(today - off, 0, 0) }
    }

    /** Consecutive days (ending today, or yesterday if today is empty) with at least [minMinutes] focused. */
    fun streak(minMinutes: Int = 10): Int {
        val m = load()
        var day = LocalDate.now().toEpochDay()
        if ((m[day]?.minutes ?: 0) < minMinutes) day--
        var n = 0
        while ((m[day]?.minutes ?: 0) >= minMinutes) { n++; day-- }
        return n
    }

    fun bestStreak(minMinutes: Int = 10): Int {
        val days = load().values.filter { it.minutes >= minMinutes }.map { it.epochDay }.sorted()
        var best = 0; var cur = 0; var prev = Long.MIN_VALUE
        for (d in days) { cur = if (d == prev + 1) cur + 1 else 1; best = maxOf(best, cur); prev = d }
        return best
    }

    /** Award titles earned right now (hours and streaks). */
    fun earned(): Set<String> {
        val hrs = totalMinutes() / 60
        val best = bestStreak()
        return (HOUR_AWARDS.filter { hrs >= it.hours } + STREAK_AWARDS.filter { best >= it.hours }).map { it.title }.toSet()
    }
}
