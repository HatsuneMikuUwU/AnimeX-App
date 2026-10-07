@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.search

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.image.cropview.CropType
import com.image.cropview.EdgeType
import com.image.cropview.ImageCropView
import com.image.cropview.rememberSaveableImageCrop
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.ExpressiveToggleChip

private val cropOptions =
    listOf(
        CropType.FREE_STYLE to "Bebas",
        CropType.SQUARE to "Persegi",
        CropType.RATIO_3_2 to "3:2",
        CropType.RATIO_4_3 to "4:3",
        CropType.RATIO_16_9 to "16:9",
        CropType.RATIO_9_16 to "9:16",
    )

@Composable
fun ImageCropScreen(
    uri: Uri,
    onBack: () -> Unit,
    onCrop: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(uri) { mutableStateOf(false) }
    var cropType by rememberSaveable { mutableStateOf(CropType.SQUARE) }

    LaunchedEffect(uri) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(context.contentResolver, uri),
                )
            } else {
                context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            }
        }.onSuccess {
            bitmap = it
        }.onFailure {
            loadFailed = true
        }
    }

    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()

    Dialog(
        onDismissRequest = onBack,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val source = bitmap
            val imageCrop = source?.let { rememberSaveableImageCrop(it) }

            Scaffold(
                modifier = Modifier.padding(systemBarsPadding),
                contentWindowInsets = WindowInsets(0),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { Text("Sesuaikan gambar", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            FilledTonalIconButton(
                                onClick = onBack,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                shapes = IconButtonDefaults.shapes(),
                                colors =
                                    IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Batal")
                            }
                        },
                        actions = {
                            FilledTonalIconButton(
                                onClick = { imageCrop?.resetView() },
                                enabled = imageCrop != null,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                shapes = IconButtonDefaults.shapes(),
                                colors =
                                    IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                            ) {
                                Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset")
                            }
                        },
                        windowInsets = WindowInsets(0),
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                    )
                },
            ) { pad ->
                Column(Modifier.padding(pad).fillMaxSize()) {
                    if (source == null || imageCrop == null) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            if (loadFailed) {
                                Text("Gambar tidak bisa dibuka", color = MaterialTheme.colorScheme.error)
                            } else {
                                AppLoadingIndicator()
                            }
                        }
                    } else {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ImageCropView(
                                imageCrop = imageCrop,
                                modifier = Modifier.fillMaxSize(),
                                guideLineColor = MaterialTheme.colorScheme.primary,
                                guideLineWidth = 2.dp,
                                edgeCircleSize = 6.dp,
                                showGuideLines = true,
                                cropType = cropType,
                                edgeType = EdgeType.CIRCULAR,
                                enableZoom = true,
                            )
                        }

                        CropTypeRow(
                            selected = cropType,
                            onSelect = { cropType = it },
                        )

                        Button(
                            onClick = { onCrop(imageCrop.onCrop(cropSourceImage = true)) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .heightIn(min = ButtonDefaults.MediumContainerHeight),
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)),
                            )
                            Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)))
                            Text("Cari dari area ini", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CropTypeRow(
    selected: CropType,
    onSelect: (CropType) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cropOptions.forEach { (type, label) ->
            ExpressiveToggleChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = label,
            )
        }
    }
}
