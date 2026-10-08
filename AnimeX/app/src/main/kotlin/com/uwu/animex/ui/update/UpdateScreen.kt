@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.update

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.animateItem
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.BuildConfig
import com.uwu.animex.data.update.AppUpdate
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.WavyLinearProgress
import com.uwu.animex.ui.theme.appBarColor
import com.uwu.animex.ui.theme.blurEffect
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import kotlinx.coroutines.launch

@Composable
fun UpdateBanner(
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by AppUpdate.state.collectAsStateWithLifecycle()
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is AppUpdate.State.Available ||
            state is AppUpdate.State.Downloading ||
            state is AppUpdate.State.Ready
        ) {
        } else {
            dismissed = false
        }
    }

    val visible =
        !dismissed &&
            when (state) {
                is AppUpdate.State.Available,
                is AppUpdate.State.Downloading,
                is AppUpdate.State.Ready,
                -> true
                else -> false
            }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        val cs = MaterialTheme.colorScheme
        val (title, subtitle, container, onContainer) =
            when (val s = state) {
                is AppUpdate.State.Downloading ->
                    Quadruple(
                        "Lagi unduh update…",
                        "v${s.release.tag} · ${(s.progress * 100).toInt()}%",
                        cs.secondaryContainer,
                        cs.onSecondaryContainer,
                    )
                is AppUpdate.State.Ready ->
                    Quadruple(
                        "Update siap dipasang",
                        "v${s.release.tag} · ketuk untuk install",
                        cs.primaryContainer,
                        cs.onPrimaryContainer,
                    )
                is AppUpdate.State.Available ->
                    Quadruple(
                        "Pembaruan AnimeX tersedia",
                        "Versi baru siap dipasang",
                        cs.primaryContainer,
                        cs.onPrimaryContainer,
                    )
                else -> Quadruple("", "", cs.surface, cs.onSurface)
            }

        Card(
            onClick = onOpenDetails,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = container),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(onContainer.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state is AppUpdate.State.Downloading) {
                            Icons.Outlined.Download
                        } else {
                            Icons.Outlined.NewReleases
                        },
                        contentDescription = null,
                        tint = onContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = onContainer.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state is AppUpdate.State.Downloading) {
                        Spacer(Modifier.height(8.dp))
                        WavyLinearProgress(
                            progress = { (state as AppUpdate.State.Downloading).progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val state by AppUpdate.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        AppUpdate.init(ctx)
        if (state is AppUpdate.State.Idle || state is AppUpdate.State.Checking) {
            AppUpdate.check()
        }
    }

    val release: AppUpdate.Release?
    val older: List<AppUpdate.Release>
    val progress: Float?
    when (val s = state) {
        is AppUpdate.State.Available -> {
            release = s.release
            older = s.older
            progress = null
        }
        is AppUpdate.State.Downloading -> {
            release = s.release
            older = s.older
            progress = s.progress
        }
        is AppUpdate.State.Ready -> {
            release = s.release
            older = s.older
            progress = 1f
        }
        else -> {
            release = null
            older = emptyList()
            progress = null
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(backdrop, blendColor = MaterialTheme.colorScheme.background),
                expandedHeight = 160.dp,
                title = {
                    Text(
                        "Pembaruan tersedia",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        shapes = IconButtonDefaults.shapes(),
                        colors =
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Balik")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = backdrop.appBarColor(MaterialTheme.colorScheme.background),
                        scrolledContainerColor = backdrop.appBarColor(MaterialTheme.colorScheme.background),
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { pad ->
        if (release == null) {
            BlurContentBox(pad, backdrop, contentAlignment = Alignment.Center) {
                when (state) {
                    is AppUpdate.State.Checking -> AppLoadingIndicator()
                    is AppUpdate.State.Error ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                (state as AppUpdate.State.Error).message,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = { scope.launch { AppUpdate.check() } },
                                shapes = ButtonDefaults.shapes(),
                            ) { Text("Coba lagi") }
                        }
                    else ->
                        Text(
                            "Belum ada update. Kamu lagi di v${BuildConfig.VERSION_NAME}.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
            }
            return@Scaffold
        }

        BlurContentBox(pad, backdrop) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = LocalBottomInset.current),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp + LocalTopInset.current,
                            bottom = 8.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        VersionCard(release = release, isLatest = true)
                    }
                    item {
                        ChangelogBlock(body = release.body)
                    }
                    items(older, key = { it.tag }, contentType = { "release" }) { r ->
                        Column(Modifier.animateItem()) {
                            VersionCard(release = r, isLatest = false)
                            Spacer(Modifier.height(8.dp))
                            ChangelogBlock(body = r.body)
                        }
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (val s = state) {
                        is AppUpdate.State.Downloading -> {
                            WavyLinearProgress(
                                progress = { s.progress },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                            )
                            Text(
                                "Mengunduh… ${(s.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                        is AppUpdate.State.Ready -> {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                        !AppUpdate.canRequestInstall(ctx)
                                    ) {
                                        val intent =
                                            Intent(
                                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                                Uri.parse("package:${ctx.packageName}"),
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        runCatching { ctx.startActivity(intent) }
                                    } else {
                                        AppUpdate.install(ctx, s.file)
                                    }
                                },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.SystemUpdate,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                        !AppUpdate.canRequestInstall(ctx)
                                    ) {
                                        "Izinkan install"
                                    } else {
                                        "Install"
                                    },
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        else -> {
                            Button(
                                onClick = {
                                    scope.launch {
                                        AppUpdate.download(ctx, release, older)
                                    }
                                },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Unduh", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val url =
                                release.htmlUrl.ifBlank {
                                    "https://github.com/HatsuneMikuUwU/AnimeX-App/releases"
                                }
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Lihat catatan perubahan")
                    }

                    TextButton(
                        onClick = {
                            AppUpdate.skipThisVersion(release.tag)
                            onBack()
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            "Lewati versi ini",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionCard(
    release: AppUpdate.Release,
    isLatest: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cs.primaryContainer.copy(alpha = 0.55f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(cs.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.NewReleases,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Column {
            Text(
                "v${release.tag}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
            )
            if (release.publishedAt.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = cs.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        release.publishedAt,
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.primary,
                    )
                }
            }
            if (release.sizeBytes > 0 && isLatest) {
                Text(
                    formatSize(release.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChangelogBlock(body: String) {
    val text = body.trim().ifBlank { "Tidak ada catatan perubahan." }
    Column(Modifier.fillMaxWidth()) {
        text.lines().forEach { line ->
            val t = line.trim()
            if (t.isEmpty()) {
                Spacer(Modifier.height(4.dp))
            } else if (t.startsWith("#") || t.startsWith("##")) {
                Text(
                    t.trimStart('#').trim(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            } else if (t.startsWith("•") || t.startsWith("-") || t.startsWith("*")) {
                Text(
                    "• ${t.drop(1).trim()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            } else {
                Text(
                    t,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String =
    when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }

@Composable
fun UpdateCheckerHost() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        AppUpdate.init(ctx)
        AppUpdate.scheduleBackgroundCheck(ctx)
        AppUpdate.check()
    }
}
