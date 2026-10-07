package com.uwu.animex.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ConnectivityMonitor {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    @Volatile
    private var started = false
    private val active = LinkedHashSet<Network>()

    fun isOnline(): Boolean = _online.value

    @Synchronized
    fun init(context: Context) {
        if (started) return
        val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        started = true
        _online.value = hasInternet(cm)
        val request =
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        runCatching {
            cm.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        synchronized(active) {
                            active += network
                            _online.value = true
                        }
                    }

                    override fun onLost(network: Network) {
                        synchronized(active) {
                            active -= network
                            _online.value = active.isNotEmpty()
                        }
                    }
                },
            )
        }
    }

    private fun hasInternet(cm: ConnectivityManager): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
