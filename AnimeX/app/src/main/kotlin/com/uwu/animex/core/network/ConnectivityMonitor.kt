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
    private val validatedNetworks = LinkedHashSet<Network>()

    fun isOnline(): Boolean = _online.value

    @Synchronized
    fun init(context: Context) {
        if (started) return
        val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        _online.value = hasValidatedInternet(cm)
        val request =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        try {
            cm.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        synchronized(validatedNetworks) {
                            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                            ) {
                                validatedNetworks += network
                            } else {
                                validatedNetworks -= network
                            }
                            _online.value = validatedNetworks.isNotEmpty()
                        }
                    }

                    override fun onLost(network: Network) {
                        synchronized(validatedNetworks) {
                            validatedNetworks -= network
                            _online.value = validatedNetworks.isNotEmpty()
                        }
                    }
                },
            )
            started = true
        } catch (_: SecurityException) {
            // Keep the initial connectivity snapshot; a later init attempt may register successfully.
            started = false
        } catch (_: RuntimeException) {
            started = false
        }
    }

    private fun hasValidatedInternet(cm: ConnectivityManager): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
