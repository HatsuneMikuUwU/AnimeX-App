package com.uwu.animex.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** Jembatan Intent notifikasi -> navigasi Compose. Nilai = id anime yang harus dibuka. */
object NotificationRouter {
    val pendingDetail = MutableStateFlow<String?>(null)
}
