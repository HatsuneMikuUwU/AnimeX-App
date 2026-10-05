package com.uwu.animex.ui.search

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.image.cropview.CropType
import com.image.cropview.EdgeType
import com.image.cropview.ImageCropView
import com.image.cropview.rememberSaveableImageCrop

@Composable
fun ImageCropScreen(
    uri: Uri,
    onBack: () -> Unit,
    onCrop: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(context.contentResolver, uri),
                ) { decoder, _, _ ->
                    decoder.setTargetConfig(Bitmap.Config.ARGB_8888)
                }
            } else {
                context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
            }
        }.onSuccess {
            bitmap = it
        }.onFailure {
            loadFailed = true
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.Close, contentDescription = "Batal")
                }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text("Sesuaikan gambar", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Geser, cubit, atau tarik sudut untuk memilih area terbaik",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Filled.Crop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }

            val source = bitmap
            if (source == null) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    if (loadFailed) {
                        Text("Gambar tidak bisa dibuka", color = MaterialTheme.colorScheme.error)
                    } else {
                        CircularProgressIndicator()
                    }
                }
            } else {
                val imageCrop = rememberSaveableImageCrop(source)
                ImageCropView(
                    imageCrop = imageCrop,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                    guideLineColor = MaterialTheme.colorScheme.primary,
                    guideLineWidth = 2.dp,
                    edgeCircleSize = 8.dp,
                    showGuideLines = true,
                    cropType = CropType.SQUARE,
                    edgeType = EdgeType.SQUARE,
                    enableZoom = true,
                )
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.ZoomIn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Area persegi",
                        Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { onCrop(imageCrop.onCrop(cropSourceImage = true)) },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text("Cari dari area ini")
                    }
                }
            }
        }
    }
}
