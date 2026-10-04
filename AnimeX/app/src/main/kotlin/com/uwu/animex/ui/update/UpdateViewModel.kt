package com.uwu.animex.ui.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.update.AppUpdate
import java.io.File
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Satu-satunya pintu UI ke alur update aplikasi (cek, unduh, install). */
class UpdateViewModel(private val appContext: Context) : ViewModel() {

    val state: StateFlow<AppUpdate.State> = AppUpdate.state

    /** Dipanggil sekali saat app mulai: jadwalkan cek berkala + cek langsung. */
    fun startBackgroundChecks() {
        AppUpdate.init(appContext)
        AppUpdate.scheduleBackgroundCheck(appContext)
        check()
    }

    /** Dipanggil saat halaman update dibuka; cek hanya kalau belum ada hasil. */
    fun ensureChecked() {
        AppUpdate.init(appContext)
        val s = state.value
        if (s is AppUpdate.State.Idle || s is AppUpdate.State.Checking) check()
    }

    fun check() {
        viewModelScope.launch { AppUpdate.check() }
    }

    fun download(release: AppUpdate.Release, older: List<AppUpdate.Release>) {
        viewModelScope.launch { AppUpdate.download(appContext, release, older) }
    }

    fun install(file: File) = AppUpdate.install(appContext, file)

    fun canRequestInstall(): Boolean = AppUpdate.canRequestInstall(appContext)

    fun skipThisVersion(tag: String) = AppUpdate.skipThisVersion(tag)
}
