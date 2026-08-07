package com.phonesync.app.ui.common

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Human-friendly byte sizes, e.g. "12.4 MB". Pure/testable — no Android framework dependency. */
fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "0 B"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

/**
 * Friendly relative timestamp, e.g. "Just now", "5 minutes ago", "Yesterday". Falls back to a
 * localized medium date for anything older than a few weeks. Pure/testable — takes [nowMs]
 * explicitly instead of reading the clock internally.
 */
fun formatRelativeTime(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (epochMs <= 0L) return "Never"
    val diffMs = nowMs - epochMs
    if (diffMs < 0L) return "Just now"

    val seconds = diffMs / 1000
    if (seconds < 60) return "Just now"

    val minutes = seconds / 60
    if (minutes < 60) return if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"

    val hours = minutes / 60
    if (hours < 24) return if (hours == 1L) "1 hour ago" else "$hours hours ago"

    val days = hours / 24
    if (days < 7) return if (days == 1L) "Yesterday" else "$days days ago"

    val weeks = days / 7
    if (weeks < 5) return if (weeks == 1L) "1 week ago" else "$weeks weeks ago"

    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))
}

/** Simple 0f..1f completion fraction; 1f (fully synced, or nothing to sync yet) when nothing is pending. */
fun completionFraction(pendingCount: Int, backedUpTotalCount: Int): Float {
    val total = pendingCount + backedUpTotalCount
    if (total <= 0) return 1f
    return (backedUpTotalCount.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}
