package com.uwu.animex.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Shared IO scope for local data stores (Room writers / collectors). */
object AppScope {
    val io: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
