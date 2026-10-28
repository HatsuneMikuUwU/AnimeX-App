package com.uwu.animex.data.anilist

import java.util.concurrent.atomic.AtomicInteger

internal class LibraryRefreshVersion {
    private val value = AtomicInteger(0)

    fun capture(): Int = value.get()

    fun isCurrent(captured: Int): Boolean = value.get() == captured

    @Synchronized
    fun commitIfCurrent(
        captured: Int,
        commit: () -> Unit,
    ): Boolean {
        if (!isCurrent(captured)) return false
        commit()
        return true
    }

    @Synchronized
    fun invalidate() {
        value.incrementAndGet()
    }

    @Synchronized
    fun invalidateAnd(clear: () -> Unit) {
        value.incrementAndGet()
        clear()
    }
}
