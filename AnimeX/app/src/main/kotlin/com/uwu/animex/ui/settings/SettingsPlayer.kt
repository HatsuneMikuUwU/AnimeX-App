@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.uwu.animex.ui.settings

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.PlayerOrientation
import com.uwu.animex.data.local.PlayerPrefs

@Composable
internal fun PlayerGroup() {
    val cs = MaterialTheme.colorScheme
    val gesturesEnabled by PlayerPrefs.gesturesEnabled.collectAsStateWithLifecycle()
    val progressPref by PlayerPrefs.progressPreference.collectAsStateWithLifecycle()
    val switchOnFail by PlayerPrefs.switchOnFailure.collectAsStateWithLifecycle()
    val orientation by PlayerPrefs.defaultOrientation.collectAsStateWithLifecycle()
    val showLoading by PlayerPrefs.showLoadingCircle.collectAsStateWithLifecycle()
    val rememberLevels by PlayerPrefs.rememberLevels.collectAsStateWithLifecycle()
    val enablePip by PlayerPrefs.enablePip.collectAsStateWithLifecycle()
    val autoplay by PlayerPrefs.autoplayEnabled.collectAsStateWithLifecycle()
    val controlsTimeout by PlayerPrefs.controlsTimeoutMs.collectAsStateWithLifecycle()
    val displayVolPer by PlayerPrefs.displayVolPer.collectAsStateWithLifecycle()
    val invertDuration by PlayerPrefs.invertDuration.collectAsStateWithLifecycle()
    val playerFullscreen by PlayerPrefs.playerFullscreen.collectAsStateWithLifecycle()
    val reduceMotion by PlayerPrefs.reduceMotion.collectAsStateWithLifecycle()
    val alwaysExternal by PlayerPrefs.alwaysUseExternal.collectAsStateWithLifecycle()
    val swapVolBright by PlayerPrefs.swapVolumeBrightness.collectAsStateWithLifecycle()
    val swapIndicator by PlayerPrefs.swapIndicator.collectAsStateWithLifecycle()
    val externalPkg by PlayerPrefs.externalPackage.collectAsStateWithLifecycle()
    val volumeBoost by PlayerPrefs.volumeBoost.collectAsStateWithLifecycle()
    val audioFocus by PlayerPrefs.audioFocus.collectAsStateWithLifecycle()
    val pauseOnNoisy by PlayerPrefs.pauseOnNoisy.collectAsStateWithLifecycle()
    val backgroundPlay by PlayerPrefs.backgroundPlay.collectAsStateWithLifecycle()
    var showProgressDialog by rememberSaveable { mutableStateOf(false) }
    var showOrientDialog by rememberSaveable { mutableStateOf(false) }
    var showTimeoutDialog by rememberSaveable { mutableStateOf(false) }
    var showExternalDialog by rememberSaveable { mutableStateOf(false) }

    SettingGroup(icon = Icons.Outlined.PlayCircle, title = "Pemutar", initiallyExpanded = false) {
        PrefItem(
            icon = Icons.Outlined.Percent,
            title = "Kapan episode dianggap udah ditonton",
            subtitle = "${(progressPref * 100).toInt()}%",
            shape = itemShape(0, 21),
            container = cs.surfaceContainerHigh,
            content = cs.onSurface,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showProgressDialog = true },
        )
        SwitchItem(
            icon = Icons.Outlined.Sync,
            title = "Ganti kualitas kalau gagal load",
            subtitle = "Kalau video error, otomatis coba kualitas lain",
            shape = itemShape(1, 21),
            checked = switchOnFail,
            enabled = true,
            onChange = PlayerPrefs::setSwitchOnFailure,
        )
        PrefItem(
            icon = Icons.Outlined.ScreenRotation,
            title = "Orientasi layar penuh",
            subtitle = orientation.label,
            shape = itemShape(2, 21),
            container = cs.surfaceContainerHigh,
            content = cs.onSurface,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showOrientDialog = true },
        )
        SwitchItem(
            icon = Icons.Outlined.HourglassEmpty,
            title = "Tunjukin loading di tengah",
            subtitle = "Lingkaran loading pas video lagi nunggu",
            shape = itemShape(3, 21),
            checked = showLoading,
            enabled = true,
            onChange = PlayerPrefs::setShowLoadingCircle,
        )
        SwitchItem(
            icon = Icons.Outlined.BrightnessMedium,
            title = "Ingat kecerahan & volume terakhir",
            subtitle = "Pas buka player, kecerahan dan volume balik ke yang kemarin",
            shape = itemShape(4, 21),
            checked = rememberLevels,
            enabled = true,
            onChange = PlayerPrefs::setRememberLevels,
        )
        SwitchItem(
            icon = Icons.Outlined.PictureInPictureAlt,
            title = "Picture-in-Picture (PiP)",
            subtitle = "Nonton di jendela kecil di atas app lain, otomatis aktif pas tekan Home atau Kembali (kalau lagi play)",
            shape = itemShape(5, 21),
            checked = enablePip,
            enabled = true,
            onChange = PlayerPrefs::setEnablePip,
        )
        SwitchItem(
            icon = Icons.Outlined.PlayArrow,
            title = "Autoplay episode berikutnya",
            subtitle = "Abis selesai, hitung mundur terus gas ke episode berikutnya",
            shape = itemShape(6, 21),
            checked = autoplay,
            enabled = true,
            onChange = PlayerPrefs::setAutoplayEnabled,
        )
        SwitchItem(
            icon = Icons.Outlined.Schedule,
            title = "Balik tampilan durasi",
            subtitle = "Kiri = sisa waktu, kanan = posisi sekarang",
            shape = itemShape(7, 21),
            checked = invertDuration,
            enabled = true,
            onChange = PlayerPrefs::setInvertDuration,
        )
        SwitchItem(
            icon = Icons.Outlined.Fullscreen,
            title = "Layar penuh",
            subtitle = "Sembunyiin status bar & navigasi pas mode layar penuh",
            shape = itemShape(8, 21),
            checked = playerFullscreen,
            enabled = true,
            onChange = PlayerPrefs::setPlayerFullscreen,
        )
        SwitchItem(
            icon = Icons.Outlined.SlowMotionVideo,
            title = "Kurangin animasi",
            subtitle = "Animasi kontrol lebih cepet / hampir gak ada",
            shape = itemShape(9, 21),
            checked = reduceMotion,
            enabled = true,
            onChange = PlayerPrefs::setReduceMotion,
        )
        SwitchItem(
            icon = Icons.AutoMirrored.Outlined.OpenInNew,
            title = "Selalu buka di player eksternal",
            subtitle = "Stream direct langsung dibuka di VLC/MX/dll, bukan di sini",
            shape = itemShape(10, 21),
            checked = alwaysExternal,
            enabled = true,
            onChange = PlayerPrefs::setAlwaysUseExternal,
        )
        PrefItem(
            icon = Icons.Outlined.Apps,
            title = "Player eksternal favorit",
            subtitle = if (externalPkg.isBlank()) "Biar sistem yang milih" else externalPkg,
            shape = itemShape(11, 21),
            container = cs.surfaceContainerHigh,
            content = cs.onSurface,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showExternalDialog = true },
        )
        SwitchItem(
            icon = Icons.Outlined.TouchApp,
            title = "Gestur player",
            subtitle = "Double tap, tahan buat 2x, geser seek / kecerahan / volume",
            shape = itemShape(12, 21),
            checked = gesturesEnabled,
            enabled = true,
            onChange = PlayerPrefs::setGesturesEnabled,
        )
        SwitchItem(
            icon = Icons.Outlined.SwapVert,
            title = "Tukar geser volume & kecerahan",
            subtitle = "Kiri = volume, kanan = kecerahan (default kebalikannya)",
            shape = itemShape(13, 21),
            checked = swapVolBright,
            enabled = gesturesEnabled,
            onChange = PlayerPrefs::setSwapVolumeBrightness,
        )
        SwitchItem(
            icon = Icons.Outlined.SwapVert,
            title = "Tukar posisi indikator",
            subtitle = "Indikator volume & kecerahan muncul di sisi layar yang berlawanan",
            shape = itemShape(14, 21),
            checked = swapIndicator,
            enabled = gesturesEnabled,
            onChange = PlayerPrefs::setSwapIndicator,
        )
        SwitchItem(
            icon = Icons.AutoMirrored.Outlined.VolumeUp,
            title = "Volume & kecerahan ditampilin persen",
            subtitle = "Geser volume atau kecerahan nunjukin 0–100%",
            shape = itemShape(15, 21),
            checked = displayVolPer,
            enabled = gesturesEnabled,
            onChange = PlayerPrefs::setDisplayVolPer,
        )
        SwitchItem(
            icon = Icons.AutoMirrored.Outlined.VolumeUp,
            title = "Penguat volume",
            subtitle = "Geser volume lagi setelah 100% buat naik sampai 200%. Kalau suara pecah, turunin lagi",
            shape = itemShape(16, 21),
            checked = volumeBoost,
            enabled = gesturesEnabled,
            onChange = PlayerPrefs::setVolumeBoost,
        )
        SwitchItem(
            icon = Icons.Outlined.MusicNote,
            title = "Fokus audio",
            subtitle = "Video otomatis jeda kalau app lain (musik, telepon) lagi bunyi",
            shape = itemShape(17, 21),
            checked = audioFocus,
            enabled = true,
            onChange = PlayerPrefs::setAudioFocus,
        )
        SwitchItem(
            icon = Icons.Outlined.Headphones,
            title = "Jeda saat headset dicabut",
            subtitle = "Biar suara gak tiba-tiba keluar dari speaker",
            shape = itemShape(18, 21),
            checked = pauseOnNoisy,
            enabled = true,
            onChange = PlayerPrefs::setPauseOnNoisy,
        )
        SwitchItem(
            icon = Icons.Outlined.PlayCircle,
            title = "Putar di latar belakang",
            subtitle = "Audio tetap jalan pas layar mati atau app ditutup, kontrolnya ada di notifikasi",
            shape = itemShape(19, 21),
            checked = backgroundPlay,
            enabled = true,
            onChange = PlayerPrefs::setBackgroundPlay,
        )
        PrefItem(
            icon = Icons.Outlined.Timer,
            title = "Kontrol hilang setelah",
            subtitle = "${controlsTimeout / 1000} detik",
            shape = itemShape(20, 21),
            container = cs.surfaceContainerHigh,
            content = cs.onSurface,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showTimeoutDialog = true },
        )
    }

    if (showTimeoutDialog) {
        data class TimeoutOpt(
            val ms: Int,
            val label: String,
        )
        val options = listOf(
            TimeoutOpt(2000, "2 detik"),
            TimeoutOpt(3000, "3 detik"),
            TimeoutOpt(4000, "4 detik"),
            TimeoutOpt(5000, "5 detik"),
            TimeoutOpt(8000, "8 detik"),
            TimeoutOpt(10000, "10 detik"),
        )
        val selected = options.firstOrNull { it.ms == controlsTimeout } ?: options[2]
        OptionDialog(
            icon = Icons.Outlined.Timer,
            title = "Kontrol hilang setelah",
            options = options,
            selected = selected,
            label = { it.label },
            onSelect = { choice -> PlayerPrefs.setControlsTimeoutMs(choice.ms) },
            onDismiss = { showTimeoutDialog = false },
        )
    }

    if (showExternalDialog) {
        val ctx = LocalContext.current
        val installed = remember {
            com.uwu.animex.ui.player.ExternalPlayer
                .installed(ctx)
        }

        data class ExtOpt(
            val pkg: String,
            val label: String,
        )
        val options = listOf(ExtOpt("", "Biar sistem yang milih")) + installed.map { ExtOpt(it.packageName, it.label) }
        val selected = options.firstOrNull { it.pkg == externalPkg } ?: options.first()
        OptionDialog(
            icon = Icons.Outlined.Apps,
            title = "Player eksternal favorit",
            options = options,
            selected = selected,
            label = { it.label },
            onSelect = { choice -> PlayerPrefs.setExternalPackage(choice.pkg) },
            onDismiss = { showExternalDialog = false },
        )
    }

    if (showProgressDialog) {
        data class ProgressOpt(
            val value: Float,
            val label: String,
        )
        val options = listOf(
            ProgressOpt(1.00f, "100%"),
            ProgressOpt(0.95f, "95%"),
            ProgressOpt(0.90f, "90%"),
            ProgressOpt(0.85f, "85%"),
            ProgressOpt(0.80f, "80%"),
            ProgressOpt(0.75f, "75%"),
            ProgressOpt(0.70f, "70%"),
        )
        val selected = options.firstOrNull { it.value == progressPref } ?: options[3]
        OptionDialog(
            icon = Icons.Outlined.Percent,
            title = "Kapan episode dianggap udah ditonton",
            options = options,
            selected = selected,
            label = { it.label },
            onSelect = { choice -> PlayerPrefs.setProgressPreference(choice.value) },
            onDismiss = { showProgressDialog = false },
        )
    }

    if (showOrientDialog) {
        OptionDialog(
            icon = Icons.Outlined.ScreenRotation,
            title = "Orientasi layar penuh",
            options = PlayerOrientation.entries,
            selected = orientation,
            label = { it.label },
            onSelect = { choice -> PlayerPrefs.setDefaultOrientation(choice) },
            onDismiss = { showOrientDialog = false },
        )
    }
}
