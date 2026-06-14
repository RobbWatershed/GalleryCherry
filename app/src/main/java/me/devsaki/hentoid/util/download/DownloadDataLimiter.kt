package me.devsaki.hentoid.util.download

import java.util.concurrent.atomic.AtomicLong

object DownloadDataLimiter {
    var limit = 0L
    private var consumedA = AtomicLong(0L)

    fun reset() {
        limit = 0L
        consumedA.set(0L)
    }

    val consumed: Long
        get() = consumedA.get()

    fun isLimitReached(): Boolean {
        return limit > 0 && consumedA.get() >= limit
    }

    fun consumeBytes(bytes: Long, isMobile: Boolean): Boolean {
        if (limit > 0 && isMobile)
            return (consumedA.addAndGet(bytes) >= limit)
        return true
    }
}