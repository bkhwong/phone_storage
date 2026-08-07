package com.phonesync.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun formatBytes_belowKilobyte() {
        assertEquals("512 B", formatBytes(512))
    }

    @Test
    fun formatBytes_kilobytes() {
        assertEquals("1.5 KB", formatBytes(1536))
    }

    @Test
    fun formatBytes_megabytes() {
        assertEquals("2.0 MB", formatBytes(2 * 1024 * 1024))
    }

    @Test
    fun formatBytes_gigabytes() {
        assertEquals("1.50 GB", formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun formatBytes_negative_isZero() {
        assertEquals("0 B", formatBytes(-5))
    }

    @Test
    fun formatRelativeTime_never() {
        assertEquals("Never", formatRelativeTime(0))
    }

    @Test
    fun formatRelativeTime_justNow() {
        val now = 1_000_000L
        assertEquals("Just now", formatRelativeTime(now - 5_000, now))
    }

    @Test
    fun formatRelativeTime_futureTimestamp_isJustNow() {
        // Clock skew guard: a timestamp slightly ahead of "now" shouldn't render as negative.
        val now = 1_000_000L
        assertEquals("Just now", formatRelativeTime(now + 5_000, now))
    }

    @Test
    fun formatRelativeTime_minutesAgo() {
        val now = 1_000_000_000L
        val fiveMinutesMs = 5 * 60_000L
        assertEquals("5 minutes ago", formatRelativeTime(now - fiveMinutesMs, now))
    }

    @Test
    fun formatRelativeTime_oneMinuteAgo_singular() {
        val now = 1_000_000_000L
        assertEquals("1 minute ago", formatRelativeTime(now - 60_000L, now))
    }

    @Test
    fun formatRelativeTime_hoursAgo() {
        val now = 1_000_000_000L
        val threeHoursMs = 3 * 3_600_000L
        assertEquals("3 hours ago", formatRelativeTime(now - threeHoursMs, now))
    }

    @Test
    fun formatRelativeTime_yesterday() {
        val now = 10_000_000_000L
        val oneDayMs = 24 * 3_600_000L
        assertEquals("Yesterday", formatRelativeTime(now - oneDayMs, now))
    }

    @Test
    fun formatRelativeTime_daysAgo() {
        val now = 10_000_000_000L
        val threeDaysMs = 3 * 24 * 3_600_000L
        assertEquals("3 days ago", formatRelativeTime(now - threeDaysMs, now))
    }

    @Test
    fun formatRelativeTime_weeksAgo() {
        val now = 10_000_000_000L
        val twoWeeksMs = 14 * 24 * 3_600_000L
        assertEquals("2 weeks ago", formatRelativeTime(now - twoWeeksMs, now))
    }

    @Test
    fun completionFraction_nothingTracked_isComplete() {
        assertEquals(1f, completionFraction(pendingCount = 0, backedUpTotalCount = 0))
    }

    @Test
    fun completionFraction_allPending_isZero() {
        assertEquals(0f, completionFraction(pendingCount = 10, backedUpTotalCount = 0))
    }

    @Test
    fun completionFraction_halfDone() {
        assertEquals(0.5f, completionFraction(pendingCount = 5, backedUpTotalCount = 5))
    }

    @Test
    fun completionFraction_allBackedUp_isOne() {
        assertEquals(1f, completionFraction(pendingCount = 0, backedUpTotalCount = 20))
    }
}
