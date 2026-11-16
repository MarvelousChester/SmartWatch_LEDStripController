package com.example.myled.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Delivers at most one value per [intervalMs] with trailing-edge delivery, so the last value is
 * never lost. A `final` submit flushes immediately. Call from a single thread (the main thread).
 */
class TrailingThrottle<T : Any>(
    private val scope: CoroutineScope,
    private val intervalMs: Long,
    private val send: (T) -> Unit,
) {
    private var latest: T? = null
    private var lastSentNanos = 0L
    private var hasSent = false
    private var trailing: Job? = null

    fun submit(value: T, final: Boolean = false) {
        latest = value
        if (final) {
            trailing?.cancel()
            trailing = null
            flush()
            return
        }
        val waitMs = intervalMs - (System.nanoTime() - lastSentNanos) / 1_000_000
        when {
            !hasSent || waitMs <= 0 -> {
                trailing?.cancel()
                trailing = null
                flush()
            }
            trailing == null -> trailing = scope.launch {
                delay(waitMs)
                trailing = null
                flush()
            }
        }
    }

    private fun flush() {
        val v = latest ?: return
        latest = null
        hasSent = true
        lastSentNanos = System.nanoTime()
        send(v)
    }
}
