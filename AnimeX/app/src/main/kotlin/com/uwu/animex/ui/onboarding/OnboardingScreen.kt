@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.uwu.animex.ui.common.icon
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

private data class OnboardPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val heroShape: Shape,
    val accentShape: Shape,
)

private enum class Palette { Primary, Secondary, Tertiary }

private fun paletteFor(page: Int) = when (page % 3) {
    0 -> Palette.Primary
    1 -> Palette.Tertiary
    else -> Palette.Secondary
}

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pages = listOf(
        OnboardPage(
            icon = Icons.Filled.PlayArrow,
            title = "Halo, selamat datang di AnimeX!",
            body = "Nonton anime jadi gampang nih. Ngebut, bersih, dan nggak ada iklan.",
            heroShape = MaterialShapes.Cookie9Sided.toShape(),
            accentShape = MaterialShapes.Sunny.toShape(),
        ),
        OnboardPage(
            icon = Icons.Filled.Explore,
            title = "Cari apa aja, ketemu",
            body = "Ketik judul favoritmu, atau kulik lewat kategori, studio, tahun, dan tipe.",
            heroShape = MaterialShapes.Clover4Leaf.toShape(),
            accentShape = MaterialShapes.Cookie6Sided.toShape(),
        ),
        OnboardPage(
            icon = Icons.Filled.Download,
            title = "Simpen dulu, nonton nanti",
            body = "Episode ke-download di background, jadi bisa ditonton kapan aja tanpa kuota.",
            heroShape = MaterialShapes.SoftBurst.toShape(),
            accentShape = MaterialShapes.Pill.toShape(),
        ),
        OnboardPage(
            icon = Icons.Filled.Notifications,
            title = "Anti ketinggalan episode",
            body = "Sambungin ke MyAnimeList, terus dapet notif begitu episode baru rilis.",
            heroShape = MaterialShapes.Flower.toShape(),
            accentShape = MaterialShapes.Sunny.toShape(),
        ),
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isLast = pagerState.currentPage == pages.lastIndex

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onFinish() }

    fun finish() {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsAsk) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else onFinish()
    }

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(visible = !isLast, enter = fadeIn(), exit = fadeOut()) {
                    TextButton(onClick = { finish() }, shapes = ButtonDefaults.shapes()) {
                        Text("Skip", fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { index ->
                val distance = ((pagerState.currentPage - index) + pagerState.currentPageOffsetFraction)
                    .absoluteValue.coerceIn(0f, 1f)
                OnboardingPageContent(
                    page = pages[index],
                    palette = paletteFor(index),
                    distance = distance,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                PageIndicator(count = pages.size, current = pagerState.currentPage)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(
                        visible = pagerState.currentPage > 0,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        Row {
                            FilledTonalIconButton(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                },
                                modifier = Modifier.size(56.dp),
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Balik",
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                    }

                    Button(
                        onClick = {
                            if (isLast) finish()
                            else scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        AnimatedContent(
                            targetState = isLast,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "onboarding_cta",
                        ) { last ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (last) "Yuk nonton" else "Lanjut",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Icon(
                                    if (last) Icons.Filled.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardPage, palette: Palette, distance: Float) {
    val cs = MaterialTheme.colorScheme
    val heroContainer: Color
    val onHero: Color
    val accentContainer: Color
    val onAccent: Color
    when (palette) {
        Palette.Primary -> {
            heroContainer = cs.primaryContainer; onHero = cs.onPrimaryContainer
            accentContainer = cs.tertiaryContainer; onAccent = cs.onTertiaryContainer
        }
        Palette.Secondary -> {
            heroContainer = cs.secondaryContainer; onHero = cs.onSecondaryContainer
            accentContainer = cs.primaryContainer; onAccent = cs.onPrimaryContainer
        }
        Palette.Tertiary -> {
            heroContainer = cs.tertiaryContainer; onHero = cs.onTertiaryContainer
            accentContainer = cs.secondaryContainer; onAccent = cs.onSecondaryContainer
        }
    }

    val infinite = rememberInfiniteTransition(label = "onboarding_spin")
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
        label = "hero_spin",
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .graphicsLayer {
                    alpha = 1f - distance
                    val s = 1f - 0.2f * distance
                    scaleX = s
                    scaleY = s
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(248.dp)
                    .graphicsLayer { rotationZ = spin }
                    .clip(page.heroShape)
                    .background(heroContainer),
            )
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = onHero,
                modifier = Modifier.size(96.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
                    .size(76.dp)
                    .graphicsLayer { rotationZ = -spin * 1.5f }
                    .clip(page.accentShape)
                    .background(accentContainer),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 8.dp, y = (-8).dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(onAccent.copy(alpha = 0.35f)),
            )
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer { alpha = 1f - distance },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = page.body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { alpha = 1f - distance },
        )
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val selected = i == current
            val width: Dp by animateDpAsState(
                targetValue = if (selected) 36.dp else 12.dp,
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                label = "dot_w",
            )
            val color by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "dot_c",
            )
            Box(
                Modifier
                    .height(12.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
