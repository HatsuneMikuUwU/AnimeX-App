@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.uwu.animex.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.BuildConfig
import com.uwu.animex.R
import com.uwu.animex.ui.common.RotatingCookieFrame
import com.uwu.animex.ui.common.icon

private const val REPO_URL = "https://github.com/HatsuneMikuUwU/AnimeX-App"

private data class AboutHighlight(val icon: ImageVector, val title: String, val desc: String)

private data class AboutLink(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

private val HIGHLIGHTS = listOf(
    AboutHighlight(Icons.Filled.Download, "Mode offline", "Unduh episode, tonton tanpa kuota"),
    AboutHighlight(Icons.Filled.Sync, "Sinkron MAL", "Progres nonton nyambung ke MyAnimeList"),
    AboutHighlight(Icons.Filled.Notifications, "Notifikasi rilis", "Tahu begitu episode baru tayang"),
    AboutHighlight(Icons.Filled.Palette, "Material You", "Warna dinamis ngikutin tema sistem"),
)

private val STACK = listOf("Kotlin", "Jetpack Compose", "Material 3 Expressive", "Media3", "Room", "Coil")

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenUpdate: () -> Unit) {
    val uri = LocalUriHandler.current
    val links = listOf(
        AboutLink(Icons.Filled.Code, "Kode sumber", "HatsuneMikuUwU/AnimeX-App") { uri.openUri(REPO_URL) },
        AboutLink(Icons.Filled.NewReleases, "Rilis", "Catatan versi dan APK terbaru") {
            uri.openUri("$REPO_URL/releases/latest")
        },
        AboutLink(Icons.Filled.BugReport, "Laporkan kendala", "Ada bug atau usulan fitur? Kabarin di sini") {
            uri.openUri("$REPO_URL/issues")
        },
        AboutLink(Icons.Filled.SystemUpdate, "Cek pembaruan", "Kamu lagi di v${BuildConfig.VERSION_NAME}", onOpenUpdate),
    )

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                expandedHeight = 160.dp,
                title = { Text("Tentang", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Balik")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                AboutHero()
                Spacer(Modifier.height(28.dp))
                HighlightGrid()
                Spacer(Modifier.height(28.dp))
                SectionTitle("Tautan")
                LinkGroup(links)
                Spacer(Modifier.height(28.dp))
                SectionTitle("Dibangun dengan")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    STACK.forEach { StackPill(it) }
                }
                Spacer(Modifier.height(28.dp))
                DisclaimerCard()
                Spacer(Modifier.height(24.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Made for Anime Community",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AboutHero() {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RotatingCookieFrame {
            Image(
                painterResource(R.mipmap.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Image(
                painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = "Logo AnimeX",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "AnimeX",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Nonton anime jadi gampang",
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Surface(shape = CircleShape, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer) {
            Text(
                "v${BuildConfig.VERSION_NAME}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HighlightGrid() {
    val cs = MaterialTheme.colorScheme
    val leaning = RoundedCornerShape(topStart = 36.dp, topEnd = 12.dp, bottomEnd = 36.dp, bottomStart = 12.dp)
    val mirrored = RoundedCornerShape(topStart = 12.dp, topEnd = 36.dp, bottomEnd = 12.dp, bottomStart = 36.dp)
    val palette = listOf(
        cs.primaryContainer to cs.onPrimaryContainer,
        cs.secondaryContainer to cs.onSecondaryContainer,
        cs.tertiaryContainer to cs.onTertiaryContainer,
        cs.surfaceContainerHigh to cs.onSurface,
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HIGHLIGHTS.chunked(2).forEachIndexed { row, pair ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEachIndexed { col, item ->
                    val i = row * 2 + col
                    HighlightTile(
                        item = item,
                        container = palette[i].first,
                        content = palette[i].second,
                        shape = if (i == 0 || i == 3) leaning else mirrored,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HighlightTile(
    item: AboutHighlight,
    container: Color,
    content: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clip(shape).background(container).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(content),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = null, tint = container)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = content,
            )
            Text(item.desc, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

private fun groupedShape(index: Int, count: Int): Shape {
    val outer = 28.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

@Composable
private fun LinkGroup(links: List<AboutLink>) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        links.forEachIndexed { i, link ->
            Surface(
                onClick = link.onClick,
                shape = groupedShape(i, links.size),
                color = cs.surfaceContainerHigh,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(cs.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(link.icon, contentDescription = null, tint = cs.onSecondaryContainer)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            link.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            link.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StackPill(label: String) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = cs.surfaceContainerHigh, contentColor = cs.onSurface) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DisclaimerCard() {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(cs.tertiaryContainer)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = cs.onTertiaryContainer)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Aplikasi tidak resmi",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = cs.onTertiaryContainer,
            )
            Text(
                "AnimeX tidak menyimpan berkas media di server sendiri. Semua data, konten, dan materi " +
                    "hak cipta berasal dari layanan pihak ketiga dan tetap milik pemiliknya masing-masing.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onTertiaryContainer,
            )
        }
    }
}
