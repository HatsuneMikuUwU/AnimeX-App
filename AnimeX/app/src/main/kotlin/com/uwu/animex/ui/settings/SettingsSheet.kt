@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.uwu.animex.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DesignServices
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.uwu.animex.BuildConfig
import com.uwu.animex.core.cache.AppCache
import com.uwu.animex.core.network.DohProvider
import com.uwu.animex.core.network.DohSettings
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.BackgroundNotifications
import com.uwu.animex.core.notification.BatteryOptimization
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.core.notification.NotificationSettings
import com.uwu.animex.core.notification.UpdateCheckInterval
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.local.AccentPalette
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.ColorSpec
import com.uwu.animex.data.local.DataBackup
import com.uwu.animex.data.local.PaletteStyle
import com.uwu.animex.data.local.PlayerButton
import com.uwu.animex.data.local.PlayerOrientation
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.data.local.SyncSource
import com.uwu.animex.data.local.SyncSourcePref
import com.uwu.animex.data.local.ThemeMode
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogCheckRow
import com.uwu.animex.ui.common.DialogConfirmButton
import com.uwu.animex.ui.common.DialogDestructiveButton
import com.uwu.animex.ui.common.DialogOptionRow
import com.uwu.animex.ui.common.clearLoadCache
import com.uwu.animex.ui.common.withHaptic
import com.uwu.animex.ui.theme.DynamicColorSupported
import com.uwu.animex.ui.theme.FONT_SCALE_MAX
import com.uwu.animex.ui.theme.FONT_SCALE_MIN
import com.uwu.animex.ui.theme.FONT_SCALE_STEP
import com.uwu.animex.ui.theme.HOME_CARD_SCALE_MAX
import com.uwu.animex.ui.theme.HOME_CARD_SCALE_MIN
import com.uwu.animex.ui.theme.HOME_CARD_SCALE_STEP
import com.uwu.animex.ui.theme.UI_SCALE_MAX
import com.uwu.animex.ui.theme.UI_SCALE_MIN
import com.uwu.animex.ui.theme.UI_SCALE_STEP
import com.uwu.animex.ui.theme.coerceToFontScale
import com.uwu.animex.ui.theme.coerceToHomeCardScale
import com.uwu.animex.ui.theme.coerceToUiScale
import com.uwu.animex.ui.theme.paletteColorScheme
import com.uwu.animex.ui.theme.toUiScalePercent
import com.uwu.animex.ui.util.WindowBlurEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val TELEGRAM_URL = "https://t.me/uwuowoumuchannel"

private val ItemOuter = 19.dp
private val ItemInner = 4.dp
private val ItemGap = 4.dp
private val GroupCorner = 28.dp

private fun itemShape(
    index: Int,
    count: Int,
): Shape =
    RoundedCornerShape(
        topStart = if (index == 0) ItemOuter else ItemInner,
        topEnd = if (index == 0) ItemOuter else ItemInner,
        bottomStart = if (index == count - 1) ItemOuter else ItemInner,
        bottomEnd = if (index == count - 1) ItemOuter else ItemInner,
    )

@Composable
fun SettingsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenMal: () -> Unit,
    onOpenAniList: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    BackHandler(enabled = visible, onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.85f)
                        .widthIn(max = 480.dp),
                shape = RoundedCornerShape(topStart = GroupCorner, bottomStart = GroupCorner),
                color = cs.background,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                start = 10.dp,
                                end = 10.dp,
                                bottom = 10.dp,
                                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 4.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AccountsGroup(onOpenMal = onOpenMal, onOpenAniList = onOpenAniList)
                        CustomizationGroup()
                        UiScaleGroup()
                        NightModeGroup()
                        SyncSourceGroup()
                        NotificationsGroup()
                        PlayerGroup()
                        NetworkGroup()
                        StorageGroup(onOpenDownloads = onOpenDownloads)
                        AboutGroup(onOpenAbout = onOpenAbout)
                        Spacer(Modifier.height(8.dp))
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationsGroup() {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val s by NotificationPrefs.state.collectAsStateWithLifecycle()
    var showIntervalDialog by rememberSaveable { mutableStateOf(false) }
    val systemAllowed = AppNotifier.canPost(ctx)
    val fcmActive = remember { BackgroundNotifications.isFcmActive(ctx) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var restricted by remember { mutableStateOf(BatteryOptimization.isRestricted(ctx)) }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) restricted = BatteryOptimization.isRestricted(ctx)
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun change(transform: NotificationSettings.() -> NotificationSettings) {
        NotificationPrefs.update(transform)
        BackgroundNotifications.sync(ctx)
    }

    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                change { copy(enabled = true) }
            } else {
                Toast.makeText(ctx, "Izin notifikasi ditolak. Nyalain dari setelan sistem ya", Toast.LENGTH_LONG).show()
                AppNotifier.openSystemSettings(ctx)
            }
        }

    fun setMaster(on: Boolean) {
        val needsPermission =
            on &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            change { copy(enabled = on) }
        }
    }

    SettingGroup(icon = Icons.Outlined.Notifications, title = "Notifikasi", initiallyExpanded = false) {
        SwitchItem(
            icon = Icons.Outlined.NotificationsActive,
            title = "Notifikasi latar belakang",
            subtitle =
                when {
                    !systemAllowed -> "Notifikasi AnimeX lagi dimatiin di setelan sistem"
                    fcmActive -> "Update app lewat Google Push, episode baru dicek ${s.interval.label.lowercase()}"
                    else -> "Dicek berkala di latar belakang"
                },
            shape = itemShape(0, 7),
            checked = s.enabled,
            enabled = true,
            onChange = ::setMaster,
        )
        SwitchItem(
            icon = Icons.Outlined.Notifications,
            title = "Episode baru",
            subtitle = "Kabar episode terbaru dari anime yang kamu pasangin lonceng",
            shape = itemShape(1, 7),
            checked = s.episodes,
            enabled = s.enabled,
            onChange = { change { copy(episodes = it) } },
        )
        SwitchItem(
            icon = Icons.Outlined.Update,
            title = "Update aplikasi",
            subtitle = "Kabar saat ada versi AnimeX baru",
            shape = itemShape(2, 7),
            checked = s.appUpdates,
            enabled = s.enabled,
            onChange = { change { copy(appUpdates = it) } },
        )
        SwitchItem(
            icon = Icons.Outlined.Science,
            title = "Sertakan versi beta",
            subtitle = "Dapat kabar rilis beta, bukan cuma versi stabil",
            shape = itemShape(3, 7),
            checked = s.prereleases,
            enabled = s.enabled && s.appUpdates,
            onChange = { change { copy(prereleases = it) } },
        )
        PrefItem(
            icon = Icons.Outlined.Schedule,
            title = "Interval cek update",
            subtitle =
                if (fcmActive) {
                    "${s.interval.label} · buat cek episode baru"
                } else {
                    "${s.interval.label} · buat cek update app dan episode baru"
                },
            shape = itemShape(4, 7),
            container = itemContainer(),
            content = cs.onSurface,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            enabled = s.enabled && (s.appUpdates || s.episodes),
            onClick = { showIntervalDialog = true },
        )
        PrefItem(
            icon = Icons.Outlined.BatteryChargingFull,
            title = "Pembatasan baterai",
            subtitle =
                if (restricted) {
                    "Dibatasi, notif episode baru bisa telat. Ketuk buat izinin"
                } else {
                    "Gak dibatasi, notif episode baru aman"
                },
            shape = itemShape(5, 7),
            container = itemContainer(),
            content = cs.onSurface,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            enabled = restricted,
            onClick = { BatteryOptimization.requestIgnore(ctx) },
        )
        PrefItem(
            icon = Icons.Outlined.Settings,
            title = "Setelan notifikasi sistem",
            subtitle = "Atur suara, getar, dan saluran notifikasi",
            shape = itemShape(6, 7),
            container = cs.secondaryContainer,
            content = cs.onSecondaryContainer,
            onClick = { AppNotifier.openSystemSettings(ctx) },
        )
    }

    if (showIntervalDialog) {
        OptionDialog(
            icon = Icons.Outlined.Schedule,
            title = "Interval cek update",
            options = UpdateCheckInterval.entries,
            selected = s.interval,
            label = { it.label },
            onSelect = { choice -> change { copy(interval = choice) } },
            onDismiss = { showIntervalDialog = false },
        )
    }
}

@Composable
private fun PlayerGroup() {
    val cs = MaterialTheme.colorScheme
    val hidden by PlayerPrefs.hiddenButtons.collectAsStateWithLifecycle()
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
    var showDialog by rememberSaveable { mutableStateOf(false) }
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
            title = "Orientasi layar",
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
            subtitle = "Sembunyiin status bar & navigasi pas nonton",
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
        PrefItem(
            icon = Icons.Outlined.VisibilityOff,
            title = "Sembunyiin tombol",
            subtitle =
                if (hidden.isEmpty()) {
                    "Semua tombol keliatan"
                } else {
                    PlayerButton.entries.filter { it in hidden }.joinToString { it.label }
                },
            shape = itemShape(19, 21),
            container = cs.surfaceContainerHigh,
            content = cs.onSurface,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showDialog = true },
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
            title = "Orientasi layar",
            options = PlayerOrientation.entries,
            selected = orientation,
            label = { it.label },
            onSelect = { choice -> PlayerPrefs.setDefaultOrientation(choice) },
            onDismiss = { showOrientDialog = false },
        )
    }

    if (showDialog) {
        var draft by remember { mutableStateOf(hidden) }
        AppDialog(
            icon = Icons.Outlined.VisibilityOff,
            title = "Sembunyiin tombol",
            onDismiss = { showDialog = false },
            confirmButton = {
                DialogConfirmButton("OK") {
                    PlayerPrefs.setHiddenButtons(draft)
                    showDialog = false
                }
            },
            dismissButton = { DialogCancelButton(label = "Gak usah deh", onClick = { showDialog = false }) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PlayerButton.entries.forEach { button ->
                        DialogCheckRow(
                            label = button.label,
                            checked = button in draft,
                            onClick = { draft = if (button in draft) draft - button else draft + button },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun NetworkGroup() {
    val cs = MaterialTheme.colorScheme
    val selected by DohSettings.selected.collectAsStateWithLifecycle()
    var showDnsDialog by rememberSaveable { mutableStateOf(false) }

    SettingGroup(icon = Icons.Outlined.Cloud, title = "Jaringan", initiallyExpanded = false) {
        PrefItem(
            icon = Icons.Outlined.Cloud,
            title = "DNS over HTTPS",
            subtitle = "${selected.label} · ${selected.description}",
            shape = itemShape(0, 1),
            container = cs.surfaceContainerHigh,
            content = cs.onSurface,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showDnsDialog = true },
        )
    }

    if (showDnsDialog) {
        OptionDialog(
            icon = Icons.Outlined.Cloud,
            title = "DNS over HTTPS",
            options = DohProvider.entries,
            selected = selected,
            label = { "${it.label} — ${it.description}" },
            onSelect = DohSettings::setProvider,
            onDismiss = { showDnsDialog = false },
        )
    }
}

@Composable
private fun AboutGroup(onOpenAbout: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current

    SettingGroup(icon = Icons.Outlined.Forum, title = "Tentang", initiallyExpanded = false) {
        PrefItem(
            icon = Icons.Outlined.Info,
            title = "Tentang AnimeX",
            subtitle = "Versi ${BuildConfig.VERSION_NAME}, kode sumber, catatan rilis, dan lapor kendala",
            shape = itemShape(0, 2),
            container = cs.primaryContainer,
            content = cs.onPrimaryContainer,
            onClick = onOpenAbout,
        )
        PrefItem(
            icon = Icons.AutoMirrored.Outlined.Send,
            title = "MikuDayo",
            subtitle = "Ketuk buat gabung channel Telegram",
            shape = itemShape(1, 2),
            container = cs.tertiaryContainer,
            content = cs.onTertiaryContainer,
            onClick = { uri.openUri(TELEGRAM_URL) },
        )
    }
}

@Composable
private fun AccountsGroup(
    onOpenMal: () -> Unit,
    onOpenAniList: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val loggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val user by Mal.user.collectAsStateWithLifecycle()
    val aniListLoggedIn by AniList.loggedIn.collectAsStateWithLifecycle()
    val aniListUser by AniList.user.collectAsStateWithLifecycle()
    val pic = user?.picture

    SettingGroup(icon = Icons.Outlined.Sync, title = "Akun") {
        PrefItem(
            icon = Icons.Outlined.AccountCircle,
            title = if (loggedIn) user?.name ?: "Sabar bentar ya…" else "Login MAL",
            subtitle = if (loggedIn) "Ketuk buat lihat profil MAL" else "Sambungin progres nonton ke daftar MAL kamu",
            shape = itemShape(0, 2),
            container = cs.secondaryContainer,
            content = cs.onSecondaryContainer,
            onClick = onOpenMal,
            end = {
                Box(
                    Modifier
                        .padding(end = 4.dp)
                        .size(72.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(cs.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loggedIn && !pic.isNullOrBlank()) {
                        AsyncImage(
                            model = pic,
                            contentDescription = "Foto profil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Outlined.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = cs.onPrimary,
                        )
                    }
                }
            },
        )
        PrefItem(
            icon = Icons.Outlined.AccountCircle,
            title = if (aniListLoggedIn) aniListUser?.name ?: "Sabar bentar ya…" else "Login AniList",
            subtitle =
                if (aniListLoggedIn) {
                    "Ketuk buat lihat profil AniList"
                } else {
                    "Sambungin progres nonton ke daftar AniList kamu"
                },
            shape = itemShape(1, 2),
            container = cs.primaryContainer,
            content = cs.onPrimaryContainer,
            onClick = onOpenAniList,
            end = {
                Box(
                    Modifier
                        .padding(end = 4.dp)
                        .size(72.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(cs.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    if (aniListLoggedIn && !aniListUser?.avatar.isNullOrBlank()) {
                        AsyncImage(
                            model = aniListUser?.avatar,
                            contentDescription = "Foto profil AniList",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Outlined.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = cs.onPrimary,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun UiScaleGroup() {
    val cs = MaterialTheme.colorScheme
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    var showDensityDialog by rememberSaveable { mutableStateOf(false) }
    var showFontDialog by rememberSaveable { mutableStateOf(false) }

    SettingGroup(
        icon = Icons.Outlined.ZoomIn,
        title = "Skala UI",
        initiallyExpanded = false,
    ) {
        SwitchItem(
            icon = Icons.Outlined.AspectRatio,
            title = "Skala otomatis",
            subtitle = "Sesuaikan ukuran tampilan dengan lebar layar perangkat",
            shape = itemShape(0, 3),
            checked = settings.autoScaleUi,
            enabled = true,
            onChange = Appearance::setAutoScaleUi,
        )
        PrefItem(
            icon = Icons.Outlined.ZoomIn,
            title = "Skala tampilan",
            subtitle =
                if (settings.autoScaleUi) {
                    "Dimatikan saat skala otomatis aktif"
                } else {
                    "${settings.uiScale.toUiScalePercent()}% dari ukuran bawaan sistem"
                },
            shape = itemShape(1, 3),
            container = itemContainer(),
            content = cs.onSurface,
            enabled = !settings.autoScaleUi,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showDensityDialog = true },
        )
        PrefItem(
            icon = Icons.Outlined.FormatSize,
            title = "Ukuran font",
            subtitle =
                if (settings.autoScaleUi) {
                    "Dimatikan saat skala otomatis aktif"
                } else {
                    "${settings.fontScale.toUiScalePercent()}% dari ukuran bawaan"
                },
            shape = itemShape(2, 3),
            container = itemContainer(),
            content = cs.onSurface,
            enabled = !settings.autoScaleUi,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showFontDialog = true },
        )
    }

    if (showDensityDialog) {
        LiveScaleDialog(
            icon = Icons.Outlined.ZoomIn,
            title = "Skala tampilan",
            description =
                "Menskala seluruh tampilan aplikasi (elemen dan teks), di atas zoom layar sistem. " +
                    "Diterapkan setelah menekan Terapkan.",
            currentScale = settings.uiScale,
            min = UI_SCALE_MIN,
            max = UI_SCALE_MAX,
            step = UI_SCALE_STEP,
            coerce = { it.coerceToUiScale() },
            onApply = Appearance::setUiScale,
            live = false,
            onDismiss = { showDensityDialog = false },
        )
    }

    if (showFontDialog) {
        LiveScaleDialog(
            icon = Icons.Outlined.FormatSize,
            title = "Ukuran font",
            description =
                "Mengatur ukuran teks saja, di atas skala tampilan dan ukuran font sistem. " +
                    "Diterapkan setelah menekan Terapkan.",
            currentScale = settings.fontScale,
            min = FONT_SCALE_MIN,
            max = FONT_SCALE_MAX,
            step = FONT_SCALE_STEP,
            coerce = { it.coerceToFontScale() },
            onApply = Appearance::setFontScale,
            live = false,
            onDismiss = { showFontDialog = false },
        )
    }
}

@Composable
private fun CustomizationGroup() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val dynamicActive = settings.dynamicColor && DynamicColorSupported
    val spec2025Ok = settings.paletteStyle.supportsSpec2025
    var showAccentSheet by rememberSaveable { mutableStateOf(false) }
    var showStyleDialog by rememberSaveable { mutableStateOf(false) }
    var showSpecDialog by rememberSaveable { mutableStateOf(false) }
    var showCardScaleDialog by rememberSaveable { mutableStateOf(false) }

    SettingGroup(
        icon = Icons.Outlined.DesignServices,
        title = "Kustomisasi",
        initiallyExpanded = false,
    ) {
        PrefItem(
            icon = Icons.Outlined.Palette,
            title = "Skema warna",
            subtitle = "Tema aplikasi akan didasarkan pada warna yang dipilih",
            shape = itemShape(0, 10),
            container = itemContainer(),
            content = cs.onSurface,
            enabled = !dynamicActive,
            onDisabledClick = {
                Toast.makeText(ctx, "Matikan warna dinamis dulu buat ganti skema warna", Toast.LENGTH_SHORT).show()
            },
            onClick = { showAccentSheet = true },
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            end = { ColorSchemePreview() },
        )
        PrefItem(
            icon = Icons.Outlined.Style,
            title = "Gaya palet",
            subtitle = settings.paletteStyle.label,
            shape = itemShape(1, 10),
            container = itemContainer(),
            content = cs.onSurface,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showStyleDialog = true },
        )
        PrefItem(
            icon = Icons.Outlined.Tune,
            title = "Spek warna",
            subtitle =
                if (spec2025Ok) {
                    settings.colorSpec.label
                } else {
                    "${ColorSpec.SPEC_2021.label}, ${settings.paletteStyle.label} belum mendukung spek 2025"
                },
            shape = itemShape(2, 10),
            container = itemContainer(),
            content = cs.onSurface,
            enabled = spec2025Ok,
            onDisabledClick = {
                Toast.makeText(ctx, "Gaya palet ini cuma mendukung spek 2021", Toast.LENGTH_SHORT).show()
            },
            onClick = { showSpecDialog = true },
            badge = cs.primary,
            badgeTint = cs.onPrimary,
        )
        SwitchItem(
            icon = Icons.Outlined.FormatColorFill,
            title = "Warna-warna yang dinamis",
            subtitle =
                if (DynamicColorSupported) {
                    "Jika diaktifkan, warna aplikasi akan mengikuti warna wallpaper kamu (Material You)"
                } else {
                    "Butuh Android 12 ke atas"
                },
            shape = itemShape(3, 10),
            checked = dynamicActive,
            enabled = DynamicColorSupported,
            onChange = Appearance::setDynamicColor,
        )
        SwitchItem(
            icon = Icons.Outlined.Contrast,
            title = "Mode AMOLED",
            subtitle = "Latar jadi hitam total di mode gelap, lebih hemat baterai di layar OLED",
            shape = itemShape(4, 10),
            checked = settings.amoled,
            enabled = true,
            onChange = Appearance::setAmoled,
        )
        SwitchItem(
            icon = Icons.Outlined.Image,
            title = "Tema dari poster",
            subtitle = "Warna aksen mengikuti dominant color poster saat buka detail anime",
            shape = itemShape(5, 10),
            checked = settings.coverTheme,
            enabled = true,
            onChange = Appearance::setCoverTheme,
        )
        SwitchItem(
            icon = Icons.Outlined.BlurOn,
            title = "Efek blur",
            subtitle =
                if (BlurSupported) {
                    "Aktifkan efek blur untuk aplikasi"
                } else {
                    "Butuh Android 13 ke atas"
                },
            shape = itemShape(6, 10),
            checked = settings.blur && BlurSupported,
            enabled = BlurSupported,
            onChange = Appearance::setBlur,
        )
        PrefItem(
            icon = Icons.Outlined.ViewCarousel,
            title = "Ukuran poster",
            subtitle = "${settings.homeCardScale.toUiScalePercent()}% dari ukuran bawaan",
            shape = itemShape(7, 10),
            container = itemContainer(),
            content = cs.onSurface,
            badge = cs.primary,
            badgeTint = cs.onPrimary,
            onClick = { showCardScaleDialog = true },
        )
        SwitchItem(
            icon = Icons.Outlined.Tag,
            title = "Angka disingkat",
            subtitle = "Jumlah dilihat dan favorit tampil singkat, misalnya 15.571 jadi 15,5k",
            shape = itemShape(8, 10),
            checked = settings.compactNumbers,
            enabled = true,
            onChange = Appearance::setCompactNumbers,
        )
        SwitchItem(
            icon = Icons.Outlined.TouchApp,
            title = "Haptics & getaran",
            subtitle = "Getar halus saat menekan tombol, kartu, sheet, dialog, dll",
            shape = itemShape(9, 10),
            checked = settings.hapticsEnabled,
            enabled = true,
            onChange = Appearance::setHapticsEnabled,
        )
    }

    if (showCardScaleDialog) {
        LiveScaleDialog(
            icon = Icons.Outlined.ViewCarousel,
            title = "Ukuran poster",
            description =
                "Mengatur lebar kartu di daftar beranda (Lanjut Nonton, Episode Baru, Sedang Hangat, dan lainnya). " +
                    "Perubahan langsung terlihat saat slider digeser. Poster di dalam kartu ikut menyesuaikan.",
            currentScale = settings.homeCardScale,
            min = HOME_CARD_SCALE_MIN,
            max = HOME_CARD_SCALE_MAX,
            step = HOME_CARD_SCALE_STEP,
            coerce = { it.coerceToHomeCardScale() },
            onApply = Appearance::setHomeCardScale,
            onDismiss = { showCardScaleDialog = false },
        )
    }

    if (showAccentSheet) {
        AccentSheet(
            selected = settings.accent,
            onSelect = Appearance::setAccent,
            onDismiss = { showAccentSheet = false },
        )
    }

    if (showStyleDialog) {
        OptionDialog(
            icon = Icons.Outlined.Style,
            title = "Gaya palet",
            options = PaletteStyle.entries,
            selected = settings.paletteStyle,
            label = { it.label },
            onSelect = Appearance::setPaletteStyle,
            onDismiss = { showStyleDialog = false },
        )
    }

    if (showSpecDialog) {
        OptionDialog(
            icon = Icons.Outlined.Tune,
            title = "Spek warna",
            options = ColorSpec.entries,
            selected = settings.colorSpec,
            label = { it.label },
            onSelect = Appearance::setColorSpec,
            onDismiss = { showSpecDialog = false },
        )
    }
}

@Composable
private fun LiveScaleDialog(
    icon: ImageVector,
    title: String,
    description: String,
    currentScale: Float,
    min: Float,
    max: Float,
    step: Float,
    coerce: (Float) -> Float,
    onApply: (Float) -> Unit,
    onDismiss: () -> Unit,
    live: Boolean = true,
    onSaved: () -> Unit = {},
) {
    val original = rememberSaveable { coerce(currentScale) }
    var selected by rememberSaveable { mutableFloatStateOf(original) }
    var confirmed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (confirmed) onDismiss() }
    val cancel = {
        if (live && !confirmed) onApply(original)
        onDismiss()
    }
    val span = max - min
    val sliderState =
        rememberSliderState(
            value = (selected - min) / span,
            steps = Math.round(span / step) - 1,
        )

    AppDialog(
        icon = icon,
        title = title,
        onDismiss = cancel,
        confirmButton = {
            DialogConfirmButton(if (live) "Simpan" else "Terapkan") {
                confirmed = true
                if (!live && selected != original) onApply(selected)
                onDismiss()
                if (selected != original) onSaved()
            }
        },
        dismissButton = { DialogCancelButton(label = "Gak usah deh", onClick = cancel) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "${selected.toUiScalePercent()}%",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Slider(
                    state = sliderState,
                    onValueChange = {
                        val snapped = coerce(min + it * span)
                        if (live && snapped != selected) onApply(snapped)
                        selected = snapped
                        sliderState.value = (snapped - min) / span
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${min.toUiScalePercent()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${max.toUiScalePercent()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun <T> OptionDialog(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialIndex = rememberSaveable { options.indexOf(selected) }
    val initial = options.getOrNull(initialIndex) ?: selected
    var confirmed by remember { mutableStateOf(false) }
    val cancel = {
        if (!confirmed) onSelect(initial)
        onDismiss()
    }
    AppDialog(
        icon = icon,
        title = title,
        onDismiss = cancel,
        confirmButton = {
            DialogConfirmButton("OK") {
                confirmed = true
                onDismiss()
            }
        },
        dismissButton = { DialogCancelButton(label = "Gak usah deh", onClick = cancel) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                options.forEach { option ->
                    DialogOptionRow(
                        label = label(option),
                        selected = option == selected,
                        onClick = { onSelect(option) },
                    )
                }
            }
        },
    )
}

@Composable
private fun ColorSchemePreview() {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val swatch = rememberSwatchScheme(settings.accent)
    val scheme = swatch ?: MaterialTheme.colorScheme
    val primaryArc = remember(scheme) { scheme.primaryContainer.copy(alpha = 0.9f) }
    val secondaryArc = remember(scheme) { scheme.secondaryContainer.copy(alpha = 0.6f) }
    val tertiaryArc = remember(scheme) { scheme.tertiaryContainer.copy(alpha = 0.9f) }
    val backdrop = remember(scheme) { scheme.primary.copy(alpha = 0.3f) }
    Box(
        Modifier
            .padding(end = 4.dp)
            .size(72.dp)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(backdrop),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(color = primaryArc, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                drawArc(color = tertiaryArc, startAngle = 90f, sweepAngle = 90f, useCenter = true)
                drawArc(color = secondaryArc, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            }
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Ubah",
                    modifier = Modifier.size(16.dp),
                    tint = scheme.inversePrimary,
                )
            }
        }
    }
}

@Composable
private fun NightModeGroup() {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    SettingGroup(
        icon = Icons.Outlined.BrightnessMedium,
        title = "Mode malam",
        initiallyExpanded = false,
    ) {
        val options =
            listOf(
                Triple("Gelap", Icons.Outlined.DarkMode, ThemeMode.DARK),
                Triple("Terang", Icons.Outlined.LightMode, ThemeMode.LIGHT),
                Triple("Sistem", Icons.Outlined.SettingsSuggest, ThemeMode.SYSTEM),
            )
        options.forEachIndexed { index, (label, icon, mode) ->
            NightModeItem(
                title = label,
                icon = icon,
                selected = settings.mode == mode,
                shape = itemShape(index, options.size),
                onClick = { Appearance.setMode(mode) },
            )
        }
    }
}

@Composable
private fun SyncSourceGroup() {
    val pref by SyncSourcePref.source.collectAsStateWithLifecycle()
    val malLoggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val aniListLoggedIn by AniList.loggedIn.collectAsStateWithLifecycle()

    if (!malLoggedIn && !aniListLoggedIn) return
    val current = SyncSourcePref.effective(pref, malLoggedIn, aniListLoggedIn)
    SettingGroup(
        icon = Icons.Outlined.Bookmarks,
        title = "Sumber sync",
        initiallyExpanded = false,
    ) {
        val options =
            buildList {
                add(Triple("Lokal", Icons.Outlined.PhoneAndroid, SyncSource.LOCAL))
                if (malLoggedIn) add(Triple("MyAnimeList", Icons.Outlined.Cloud, SyncSource.MAL))
                if (aniListLoggedIn) add(Triple("AniList", Icons.Outlined.Cloud, SyncSource.ANILIST))
            }
        options.forEachIndexed { index, (label, icon, source) ->
            NightModeItem(
                title = label,
                icon = icon,
                selected = current == source,
                shape = itemShape(index, options.size),
                onClick = { SyncSourcePref.set(source) },
            )
        }
    }
}

@Composable
private fun StorageGroup(onOpenDownloads: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sizeBytes by remember { mutableStateOf<Long?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    var working by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) { sizeBytes = AppCache.sizeBytes(ctx) }

    val backupLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            working = true
            scope.launch {
                val result = runCatching { DataBackup.export(ctx, uri) }
                working = false
                val msg =
                    result.fold(
                        onSuccess = { "Backup selesai, ${it.total} data disimpan" },
                        onFailure = { "Gagal bikin backup: ${it.message ?: "kesalahan tak dikenal"}" },
                    )
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            }
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> if (uri != null) pendingRestore = uri }

    SettingGroup(
        icon = Icons.Outlined.Storage,
        title = "Penyimpanan",
        initiallyExpanded = false,
    ) {
        PrefItem(
            icon = Icons.Outlined.Download,
            title = "Unduhan",
            subtitle = "Episode yang diunduh buat ditonton tanpa internet",
            shape = itemShape(0, 4),
            container = itemContainer(),
            content = MaterialTheme.colorScheme.onSurface,
            badge = MaterialTheme.colorScheme.primary,
            badgeTint = MaterialTheme.colorScheme.onPrimary,
            onClick = onOpenDownloads,
        )
        PrefItem(
            icon = Icons.Outlined.Backup,
            title = "Backup data",
            subtitle =
                if (working) {
                    "Lagi memproses…"
                } else {
                    "Simpan bookmark, riwayat, progres nonton, dan pengaturan ke satu berkas"
                },
            shape = itemShape(1, 4),
            container = itemContainer(),
            content = MaterialTheme.colorScheme.onSurface,
            badge = MaterialTheme.colorScheme.primary,
            badgeTint = MaterialTheme.colorScheme.onPrimary,
            enabled = !working,
            onClick = { backupLauncher.launch(DataBackup.suggestedFileName()) },
        )
        PrefItem(
            icon = Icons.Outlined.Restore,
            title = "Restore data",
            subtitle = "Pulihkan dari berkas backup, digabung dengan data yang ada sekarang",
            shape = itemShape(2, 4),
            container = itemContainer(),
            content = MaterialTheme.colorScheme.onSurface,
            badge = MaterialTheme.colorScheme.primary,
            badgeTint = MaterialTheme.colorScheme.onPrimary,
            enabled = !working,
            onClick = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
        )
        PrefItem(
            icon = Icons.Outlined.DeleteSweep,
            title = "Hapus cache",
            subtitle =
                when {
                    clearing -> "Lagi menghapus…"
                    sizeBytes != null -> "Poster, dan data API · ${Formatter.formatShortFileSize(ctx, sizeBytes!!)}"
                    else -> "Poster, dan data API"
                },
            shape = itemShape(3, 4),
            container = itemContainer(),
            content = MaterialTheme.colorScheme.onSurface,
            badge = MaterialTheme.colorScheme.primary,
            badgeTint = MaterialTheme.colorScheme.onPrimary,
            enabled = !clearing,
            onClick = { confirm = true },
        )
    }

    pendingRestore?.let { uri ->
        AppDialog(
            icon = Icons.Outlined.Restore,
            onDismiss = { pendingRestore = null },
            title = "Restore dari backup?",
            text = {
                Text(
                    "Bookmark, riwayat, progres nonton, pengingat episode, pengaturan tampilan, dan pengaturan pemutar dari backup " +
                        "bakal digabung ke data sekarang. Data dengan id yang sama ditimpa versi backup. " +
                        "Login MAL dan unduhan offline nggak ikut.",
                )
            },
            confirmButton = {
                DialogConfirmButton("Restore") {
                    pendingRestore = null
                    working = true
                    scope.launch {
                        val result = runCatching { DataBackup.restore(ctx, uri) }
                        working = false
                        val msg =
                            result.fold(
                                onSuccess = { "Restore selesai, ${it.total} data dipulihkan" },
                                onFailure = { "Gagal restore: ${it.message ?: "kesalahan tak dikenal"}" },
                            )
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    }
                }
            },
            dismissButton = { DialogCancelButton { pendingRestore = null } },
        )
    }

    if (confirm) {
        AppDialog(
            icon = Icons.Outlined.DeleteSweep,
            onDismiss = { confirm = false },
            title = "Hapus semua cache?",
            text = {
                Text(
                    "Poster dan data API, yang kesimpen bakal dihapus, nanti diunduh lagi pas dibutuhin. " +
                        "Bookmark, riwayat nonton, dan login MAL tetap aman.",
                )
            },
            confirmButton = {
                DialogDestructiveButton("Hapus") {
                    confirm = false
                    clearing = true
                    scope.launch {
                        val freed = runCatching { AppCache.clearAll(ctx) }.getOrNull()
                        clearLoadCache()
                        sizeBytes = AppCache.sizeBytes(ctx)
                        clearing = false
                        Toast
                            .makeText(
                                ctx,
                                if (freed != null) {
                                    "Cache dihapus, ${Formatter.formatShortFileSize(ctx, freed)} dibebaskan"
                                } else {
                                    "Gagal menghapus cache"
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            },
            dismissButton = { DialogCancelButton { confirm = false } },
        )
    }
}

@Composable
private fun NightModeItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    PrefItem(
        icon = icon,
        title = title,
        subtitle = null,
        shape = shape,
        container = if (selected) cs.secondaryContainer.copy(alpha = 0.7f) else itemContainer(),
        content = if (selected) cs.onSecondaryContainer else cs.onSurface,
        onClick = onClick,
        badge = if (selected) cs.onSecondaryContainer else cs.primary,
        badgeTint = if (selected) cs.secondaryContainer else cs.onPrimary,
        end = {
            Icon(
                if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    )
}

@Composable
private fun SwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    shape: Shape,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    PrefItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        shape = shape,
        container = itemContainer(),
        content = cs.onSurface,
        enabled = enabled,
        onClick = { onChange(!checked) },
        badge = cs.primary,
        badgeTint = cs.onPrimary,
        end = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Outlined.Check else Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        },
    )
}

private const val PaletteColumns = 3

private val BlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

private val SwatchSchemeCache = ConcurrentHashMap<String, ColorScheme>()

@Composable
private fun AccentSheet(
    selected: AccentPalette,
    onSelect: (AccentPalette) -> Unit,
    onDismiss: () -> Unit,
) {
    AppModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
    ) {
        WindowBlurEffect()
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            AccentPalette.entries.chunked(PaletteColumns).forEach { rowItems ->
                Row(Modifier.fillMaxWidth()) {
                    rowItems.forEach { accent ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            AccentSwatch(accent, accent == selected) { onSelect(accent) }
                        }
                    }
                    repeat(PaletteColumns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun rememberSwatchScheme(accent: AccentPalette): ColorScheme? {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val cacheKey = "${accent.name}_${settings.paletteStyle.name}_${settings.colorSpec.name}"
    val scheme by produceState<ColorScheme?>(initialValue = SwatchSchemeCache[cacheKey], key1 = cacheKey) {
        val cached = SwatchSchemeCache[cacheKey]
        if (cached != null) {
            value = cached
        } else {
            withContext(Dispatchers.Default) {
                val computed = paletteColorScheme(accent.seed, false, settings.paletteStyle, settings.colorSpec)
                SwatchSchemeCache[cacheKey] = computed
                value = computed
            }
        }
    }
    return scheme
}

@Composable
private fun AccentSwatch(
    accent: AccentPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = rememberSwatchScheme(accent)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = withHaptic(block = onClick))
                .padding(vertical = 8.dp),
    ) {
        val current = scheme
        if (current != null) {
            SwatchContent(current, selected)
        } else {
            FallbackSwatchContent(accent.seed, selected)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            accent.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SwatchContent(
    scheme: ColorScheme,
    selected: Boolean,
) {
    val primaryArc = remember(scheme) { scheme.primaryContainer.copy(alpha = 0.9f) }
    val secondaryArc = remember(scheme) { scheme.secondaryContainer.copy(alpha = 0.6f) }
    val tertiaryArc = remember(scheme) { scheme.tertiaryContainer.copy(alpha = 0.9f) }
    val backdrop = remember(scheme) { scheme.primary.copy(alpha = 0.3f) }
    Box(
        Modifier.size(64.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(backdrop),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(color = primaryArc, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                drawArc(color = tertiaryArc, startAngle = 90f, sweepAngle = 90f, useCenter = true)
                drawArc(color = secondaryArc, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            }
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = scheme.inversePrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FallbackSwatchContent(
    base: Color,
    selected: Boolean,
) {
    Box(
        Modifier.size(64.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(base.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(base.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(base),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun itemContainer(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

@Composable
private fun SettingGroup(
    icon: ImageVector,
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "group-chevron")
    val groupColor = cs.surfaceContainer

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GroupCorner))
            .background(groupColor),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GroupCorner))
                .clickable(onClick = withHaptic { expanded = !expanded })
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = icon, container = cs.primary, tint = cs.onPrimary)
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurface,
            )
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "Ciutkan" else "Lebarkan",
                modifier = Modifier.rotate(rotation),
                tint = cs.onSurface,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(ItemGap),
                content = content,
            )
        }
    }
}

@Composable
private fun IconBadge(
    icon: ImageVector,
    container: Color,
    tint: Color,
) {
    Box(
        Modifier.size(40.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = tint)
    }
}

@Composable
private fun PrefItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    shape: Shape,
    container: Color,
    content: Color,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    onDisabledClick: (() -> Unit)? = null,
    badge: Color = content,
    badgeTint: Color = container,
    end: (@Composable () -> Unit)? = null,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.5f, label = "item-alpha")
    val effectiveClick: (() -> Unit)? =
        when {
            enabled -> onClick
            else -> onDisabledClick
        }
    val body: @Composable () -> Unit = {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(icon = icon, container = badge, tint = badgeTint)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (subtitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = content.copy(alpha = 0.8f),
                        )
                    }
                }
                end?.invoke()
            }
            bottom?.invoke(this)
        }
    }
    if (effectiveClick != null) {
        val hapticClick = withHaptic(block = effectiveClick)
        Surface(
            onClick = hapticClick,
            shape = shape,
            color = container,
            contentColor = content,
            modifier = Modifier.fillMaxWidth().alpha(alpha),
        ) { body() }
    } else {
        Surface(
            shape = shape,
            color = container,
            contentColor = content,
            modifier = Modifier.fillMaxWidth().alpha(alpha),
        ) { body() }
    }
}
