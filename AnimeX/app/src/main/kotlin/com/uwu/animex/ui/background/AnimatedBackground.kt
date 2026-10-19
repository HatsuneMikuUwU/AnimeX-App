package com.uwu.animex.ui.background

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Jenis latar animasi yang bisa dipilih di Pengaturan > Kustomisasi. */
enum class BackgroundType(
    val label: String,
    val description: String,
) {
    NONE("Mati", "Latar polos tanpa animasi"),
    CIRCLES("Lingkaran", "Lingkaran lembut yang melayang pelan"),
    RINGS("Riak", "Cincin konsentris yang beriak seperti air"),
    MESH("Mesh", "Jaring low-poly 3D yang bergelombang"),
    SPACE("Luar angkasa", "Bintang-bintang melaju ke arahmu"),
    SHAPES("Bangun 3D", "Bangun ruang yang berputar dan melayang"),
    SNOW("Salju", "Butiran salju turun dengan hembusan angin"),
    GRID("Grid", "Titik-titik yang bernapas mengikuti gelombang"),
    PARTICLES("Partikel", "Titik-titik hanyut yang saling terhubung"),
    LAVA("Lava", "Lilin lava yang naik, turun, dan menyatu"),
}

/**
 * True selama latar animasi sedang tampil di belakang layar. Layar membaca ini lewat
 * [screenContainerColor] untuk menjadi transparan supaya animasinya terlihat.
 */
val LocalAnimatedBackgroundActive = staticCompositionLocalOf { false }

/** Warna latar sebuah layar: transparan saat latar animasi aktif, selain itu warna background tema. */
@Composable
fun screenContainerColor(): Color =
    if (LocalAnimatedBackgroundActive.current) Color.Transparent else MaterialTheme.colorScheme.background

/** Menggambar latar animasi bertipe [type]. Tidak menggambar apa pun untuk [BackgroundType.NONE]. */
@Composable
fun AnimatedBackground(
    type: BackgroundType,
    modifier: Modifier = Modifier,
    enableParallax: Boolean = true,
) {
    if (type == BackgroundType.NONE) return
    // Tidak ada latar yang memakai blending antar-layer, jadi clip saja cukup
    // tanpa perlu render target offscreen layar penuh
    Box(modifier.fillMaxSize().clipToBounds()) {
        when (type) {
            BackgroundType.CIRCLES -> CirclesBackground(enableParallax = enableParallax)
            BackgroundType.RINGS -> RingsBackground(enableParallax = enableParallax)
            BackgroundType.PARTICLES -> ParticlesBackground(enableParallax = enableParallax)
            BackgroundType.MESH -> MeshBackground(enableParallax = enableParallax)
            BackgroundType.SPACE -> SpaceBackground(enableParallax = enableParallax)
            BackgroundType.SHAPES -> ShapesBackground(enableParallax = enableParallax)
            BackgroundType.SNOW -> SnowBackground(enableParallax = enableParallax)
            BackgroundType.GRID -> GridBackground(enableParallax = enableParallax)
            BackgroundType.LAVA -> LavaBackground(enableParallax = enableParallax)
            BackgroundType.NONE -> Unit
        }
    }
}

/** Tema yang sedang tampil gelap atau tidak, dibaca dari warna latar (ikut mode AMOLED dan Material You). */
@Composable
internal fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
