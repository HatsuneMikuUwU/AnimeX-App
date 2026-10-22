package com.uwu.animex.ui.background

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance
import java.util.TimeZone
import kotlin.random.Random

enum class RandomInterval(
    val label: String,
) {
    ON_LAUNCH("Tiap buka app"),
    DAILY("Tiap hari"),
    EVERY_3_DAYS("Tiap 3 hari"),
}

object RandomBackground {
    private var launchPick: BackgroundType? = null

    fun resolve(
        interval: RandomInterval,
        pool: List<BackgroundType>,
        previousLaunch: BackgroundType?,
    ): BackgroundType =
        when (interval) {
            RandomInterval.ON_LAUNCH ->
                launchPick?.takeIf { it in pool }
                    ?: pool
                        .filter { it != previousLaunch }
                        .ifEmpty { pool }
                        .random()
                        .also { launchPick = it }
            RandomInterval.DAILY -> periodPick(epochDay(), pool)
            RandomInterval.EVERY_3_DAYS -> periodPick(epochDay() / 3, pool)
        }

    private fun epochDay(): Long {
        val now = System.currentTimeMillis()
        return Math.floorDiv(now + TimeZone.getDefault().getOffset(now), 86_400_000L)
    }

    private fun periodPick(
        period: Long,
        pool: List<BackgroundType>,
    ): BackgroundType {
        val round = Math.floorDiv(period, pool.size.toLong())
        val order = roundOrder(round, pool)
        return order[Math.floorMod(period, pool.size.toLong()).toInt()]
    }

    private fun roundOrder(
        round: Long,
        pool: List<BackgroundType>,
    ): List<BackgroundType> {
        val order = pool.shuffled(Random(round)).toMutableList()
        if (order.size > 1 && order.first() == pool.shuffled(Random(round - 1)).last()) {
            order[0] = order[1].also { order[1] = order[0] }
        }
        return order
    }
}

@Composable
fun rememberEffectiveBackground(
    picked: BackgroundType,
    interval: RandomInterval,
    seasonalThemes: Boolean,
): BackgroundType {
    val event = remember { SeasonalEvent.on() }
    val resolved =
        remember(picked, interval) {
            if (picked == BackgroundType.RANDOM) {
                val previous =
                    Appearance.lastRandomBackground()?.let { name ->
                        BackgroundType.entries.firstOrNull { it.name == name }
                    }
                RandomBackground.resolve(interval, BackgroundType.RANDOMIZABLE, previous)
            } else {
                picked
            }
        }
    if (picked == BackgroundType.RANDOM && interval == RandomInterval.ON_LAUNCH) {
        LaunchedEffect(resolved) { Appearance.setLastRandomBackground(resolved.name) }
    }
    return event?.takeIf { seasonalThemes && picked != BackgroundType.NONE }?.background ?: resolved
}

@Composable
fun rememberActiveBackground(): BackgroundType {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    return rememberEffectiveBackground(settings.backgroundType, settings.randomInterval, settings.seasonalThemes)
}
