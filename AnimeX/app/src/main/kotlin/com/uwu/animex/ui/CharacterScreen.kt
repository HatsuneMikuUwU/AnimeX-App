package com.uwu.animex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.uwu.animex.data.AnimeCharacter
import com.uwu.animex.data.CharacterDetails
import com.uwu.animex.data.CharacterRepo
import com.uwu.animex.data.CharacterRole

private val PersonImageSmall = 72.dp
private val PersonImageBig = 140.dp

/** Tab list — layout AniHyou MediaCharacters: karakter kiri, VA kanan (mirrored). */
@Composable
fun CharacterListTab(
    characters: List<AnimeCharacter>,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onOpen: (AnimeCharacter) -> Unit,
) {
    when {
        loading && characters.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CenterLoading()
        }
        characters.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Karakter tidak ditemukan", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            items(characters, key = { it.id ?: it.character.name }) { item ->
                CharacterMediaRow(item) {
                    if (item.id != null) onOpen(item)
                }
            }
        }
    }
}

@Composable
private fun CharacterMediaRow(item: AnimeCharacter, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Kiri: karakter (AniHyou PersonItemHorizontal)
        PersonItemHorizontal(
            title = item.character.name,
            imageUrl = item.character.image,
            subtitle = roleLabel(item.role),
            modifier = Modifier.weight(1f),
            onClick = onClick,
        )
        // Kanan: VA mirrored (AniHyou PersonItemHorizontalMirrored)
        item.voiceActor?.let { va ->
            PersonItemHorizontalMirrored(
                title = va.name,
                imageUrl = va.image,
                subtitle = "Japanese",
                modifier = Modifier
                    .padding(end = 8.dp)
                    .weight(1f),
                onClick = { },
            )
        }
    }
}

@Composable
private fun PersonItemHorizontal(
    title: String,
    imageUrl: String?,
    subtitle: String?,
    modifier: Modifier = Modifier,
    imageSize: Dp = PersonImageSmall,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PersonImage(imageUrl, imageSize)
            Column(Modifier.padding(start = 16.dp)) {
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonItemHorizontalMirrored(
    title: String,
    imageUrl: String?,
    subtitle: String?,
    modifier: Modifier = Modifier,
    imageSize: Dp = PersonImageSmall,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Column(
                Modifier.padding(end = 16.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            PersonImage(imageUrl, imageSize)
        }
    }
}

@Composable
private fun PersonImage(url: String?, size: Dp) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

private fun roleLabel(r: CharacterRole?): String? = when (r) {
    CharacterRole.MAIN -> "Utama"
    CharacterRole.SUPPORTING -> "Pendukung"
    CharacterRole.BACKGROUND -> "Latar"
    null -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterScreen(id: Int, fallbackName: String, onBack: () -> Unit) {
    var details by remember(id) { mutableStateOf<CharacterDetails?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    val uri = LocalUriHandler.current

    LaunchedEffect(id) {
        loading = true
        error = null
        details = CharacterRepo.details(id)
        if (details == null) error = "Gagal memuat karakter"
        loading = false
    }

    val d = details
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        d?.name ?: fallbackName.ifBlank { "Karakter" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    d?.siteUrl?.let { url ->
                        FilledTonalIconButton(
                            onClick = { uri.openUri(url) },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = "Buka di AniList")
                        }
                    }
                },
            )
        },
    ) { pad ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CenterLoading()
            }
            error != null && d == null -> Box(
                Modifier.fillMaxSize().padding(pad),
                contentAlignment = Alignment.Center,
            ) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            d != null -> CharacterInfoContent(
                d,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

/** Detail — layout AniHyou CharacterInfoView. */
@Composable
private fun CharacterInfoContent(d: CharacterDetails, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PersonImage(d.image, PersonImageBig)
            Spacer(Modifier.width(8.dp))
            // PersonImage already has size; add padding like AniHyou
            SelectionContainer {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        d.name,
                        modifier = Modifier.padding(8.dp),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    d.nameNative?.takeIf { it.isNotBlank() && it != d.name }?.let {
                        Text(
                            it,
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    d.alternatives.takeIf { it.isNotEmpty() }?.let {
                        Text(
                            it.joinToString(", "),
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // Info rows seperti AniHyou InfoItemView (title kiri, value kanan)
        InfoItemRow("Ulang Tahun", d.birthLabel)
        InfoItemRow("Usia", d.age)
        InfoItemRow("Gender", d.gender)
        InfoItemRow("Golongan Darah", d.bloodType)

        d.description?.takeIf { it.isNotBlank() }?.let { desc ->
            SelectionContainer {
                Text(
                    desc,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun InfoItemRow(title: String, info: String?) {
    if (info.isNullOrBlank()) return
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                info,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
}
