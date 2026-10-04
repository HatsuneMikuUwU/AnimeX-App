package com.uwu.animex.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalUser
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Satu-satunya pintu UI ke state akun MAL (login, profil, pesan, busy). */
class ProfileViewModel : ViewModel() {

    val loggedIn: StateFlow<Boolean> = Mal.loggedIn
    val user: StateFlow<MalUser?> = Mal.user
    val message: StateFlow<String?> = Mal.message
    val busy: StateFlow<Boolean> = Mal.busy

    val profileUrlPrefix: String get() = Mal.PROFILE_URL

    fun refreshUser() {
        viewModelScope.launch { runCatching { Mal.refreshUser() } }
    }

    fun clearMessage() = Mal.clearMessage()

    fun logout() = Mal.logout()

    fun startLogin(context: Context) = Mal.startLogin(context)
}
