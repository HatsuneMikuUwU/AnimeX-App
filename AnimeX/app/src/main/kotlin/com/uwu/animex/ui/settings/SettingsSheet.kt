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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.uwu.animex.BuildConfig
import com.uwu.animex.core.cache.AppCache
import com.uwu.animex.core.network.DohProvider
import com.uwu.animex.core.network.DohSettings
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.BackgroundNotifications
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.core.notification.NotificationSettings
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.animein.AnimeInAccount
import com.uwu.animex.data.local.DataBackup
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.tracker.SyncSource
import com.uwu.animex.data.tracker.SyncSourcePref
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogConfirmButton
import com.uwu.animex.ui.common.DialogDestructiveButton
import com.uwu.animex.ui.common.clearLoadCache
import kotlinx.coroutines.launch

private const val TELEGRAM_URL = "https://t.me/uwuowoumuchannel"

@Composable
fun SettingsSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenMal: () -> Unit,
    onOpenAniList: () -> Unit,
    onOpenAnimeIn: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    BackHandler(enabled = visible, onBack = onDismiss)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = AppMotion.fadeEnter(), exit = AppMotion.fadeExit()) {
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
            enter = AppMotion.slideInEnd(),
            exit = AppMotion.slideOutEnd(),
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
                        AccountsGroup(
                            onOpenMal = onOpenMal,
                            onOpenAniList = onOpenAniList,
                            onOpenAnimeIn = onOpenAnimeIn,
                        )
                        SyncSourceGroup()
                        CustomizationGroup()
                        BackgroundGroup()
                        UiScaleGroup()
                        NightModeGroup()
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
    val systemAllowed = AppNotifier.canPost(ctx)
    val fcmActive = remember { BackgroundNotifications.isFcmActive(ctx) }

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
                    fcmActive -> "Update app lewat Google Push, episode baru diingetin pas jam tayang"
                    else -> "Episode baru diingetin pas jam tayang. Kabar update app butuh Google Play Services"
                },
            shape = itemShape(0, 5),
            checked = s.enabled,
            enabled = true,
            onChange = ::setMaster,
        )
        SwitchItem(
            icon = Icons.Outlined.Update,
            title = "Update aplikasi",
            subtitle = "Kabar saat ada versi AnimeX baru",
            shape = itemShape(1, 5),
            checked = s.appUpdates,
            enabled = s.enabled,
            onChange = { change { copy(appUpdates = it) } },
        )
        SwitchItem(
            icon = Icons.Outlined.Science,
            title = "Sertakan versi beta",
            subtitle = "Dapat kabar rilis beta, bukan cuma versi stabil",
            shape = itemShape(2, 5),
            checked = s.prereleases,
            enabled = s.enabled && s.appUpdates,
            onChange = { change { copy(prereleases = it) } },
        )
        SwitchItem(
            icon = Icons.Outlined.Notifications,
            title = "Episode baru",
            subtitle = "Kabar saat episode anime pilihanmu tayang sesuai jadwal",
            shape = itemShape(3, 5),
            checked = s.episodes,
            enabled = s.enabled,
            onChange = { change { copy(episodes = it) } },
        )
        PrefItem(
            icon = Icons.Outlined.Settings,
            title = "Setelan notifikasi sistem",
            subtitle = "Atur suara, getar, dan saluran notifikasi",
            shape = itemShape(4, 5),
            container = cs.secondaryContainer,
            content = cs.onSecondaryContainer,
            onClick = { AppNotifier.openSystemSettings(ctx) },
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
            container = cs.surfaceContainerHighest,
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
    onOpenAnimeIn: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val loggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val user by Mal.user.collectAsStateWithLifecycle()
    val aniListLoggedIn by AniList.loggedIn.collectAsStateWithLifecycle()
    val aniListUser by AniList.user.collectAsStateWithLifecycle()
    val animeInUser by AnimeInAccount.user.collectAsStateWithLifecycle()
    val pic = user?.picture

    SettingGroup(icon = Icons.Outlined.ManageAccounts, title = "Akun") {
        PrefItem(
            icon = Icons.Outlined.AccountCircle,
            title = if (loggedIn) user?.name ?: "Sabar bentar ya…" else "Login MAL",
            subtitle = if (loggedIn) "Ketuk buat lihat profil MAL" else "Sambungin progres nonton ke daftar MAL kamu",
            shape = itemShape(0, 3),
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
            shape = itemShape(1, 3),
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
        PrefItem(
            icon = Icons.Outlined.AccountCircle,
            title = animeInUser?.username?.takeIf { it.isNotBlank() } ?: "Login ANIMEIN",
            subtitle = if (animeInUser == null) {
                "Masuk dengan akun ANIMEIN"
            } else {
                "Akun ANIMEIN terhubung · ketuk untuk mengelola"
            },
            shape = itemShape(2, 3),
            container = cs.tertiaryContainer,
            content = cs.onTertiaryContainer,
            onClick = onOpenAnimeIn,
        )
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
        icon = Icons.Outlined.Sync,
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
                    sizeBytes != null -> "Poster, dan data API · ${Formatter.formatShortFileSize(ctx, sizeBytes ?: 0L)}"
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
                    "Bookmark, riwayat, progres nonton, pengingat episode, pengaturan tampilan, " +
                        "dan pengaturan pemutar dari backup " +
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
