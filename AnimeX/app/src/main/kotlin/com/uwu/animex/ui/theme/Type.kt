@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Typography Material 3 Expressive.
 *
 * Ide utamanya: skala dasar tetap tenang (weight Normal–Medium), sedangkan varian
 * "Emphasized" dipakai untuk momen yang mau ditonjolkan (judul section, nama kartu,
 * angka besar). Kontras weight antara dua kelompok ini yang bikin kesan expressive.
 *
 * - Display/Headline: tracking dirapatkan biar terasa padat dan percaya diri.
 * - Title/Label: tracking sedikit dilonggarkan supaya tetap enak dibaca di ukuran kecil.
 * - Semua style pakai LineHeightStyle trim-none + center, jadi teks sejajar rapi
 *   di dalam chip, badge, dan tombol.
 */
private val AppFont = FontFamily.SansSerif

private val centered = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = AppFont,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeightStyle = centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

val AppTypography = Typography(
    // ── Display ───────────────────────────────────────────────────────────
    displayLarge = style(57, 64, FontWeight.SemiBold, -1.0),
    displayMedium = style(45, 52, FontWeight.SemiBold, -0.75),
    displaySmall = style(36, 44, FontWeight.SemiBold, -0.5),

    // ── Headline ──────────────────────────────────────────────────────────
    headlineLarge = style(32, 40, FontWeight.SemiBold, -0.4),
    headlineMedium = style(28, 36, FontWeight.SemiBold, -0.25),
    headlineSmall = style(24, 32, FontWeight.SemiBold, -0.15),

    // ── Title ─────────────────────────────────────────────────────────────
    titleLarge = style(22, 28, FontWeight.SemiBold, 0.0),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.15),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.1),

    // ── Body ──────────────────────────────────────────────────────────────
    bodyLarge = style(16, 24, FontWeight.Normal, 0.4),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.25),
    bodySmall = style(12, 16, FontWeight.Normal, 0.4),

    // ── Label ─────────────────────────────────────────────────────────────
    labelLarge = style(14, 20, FontWeight.Medium, 0.1),
    labelMedium = style(12, 16, FontWeight.Medium, 0.5),
    labelSmall = style(11, 16, FontWeight.Medium, 0.5),

    // ── Emphasized (khas Expressive) ──────────────────────────────────────
    displayLargeEmphasized = style(57, 64, FontWeight.Black, -1.25),
    displayMediumEmphasized = style(45, 52, FontWeight.Black, -1.0),
    displaySmallEmphasized = style(36, 44, FontWeight.Black, -0.75),

    headlineLargeEmphasized = style(32, 40, FontWeight.ExtraBold, -0.5),
    headlineMediumEmphasized = style(28, 36, FontWeight.ExtraBold, -0.4),
    headlineSmallEmphasized = style(24, 32, FontWeight.ExtraBold, -0.25),

    titleLargeEmphasized = style(22, 28, FontWeight.ExtraBold, -0.1),
    titleMediumEmphasized = style(16, 24, FontWeight.ExtraBold, 0.1),
    titleSmallEmphasized = style(14, 20, FontWeight.ExtraBold, 0.1),

    bodyLargeEmphasized = style(16, 24, FontWeight.SemiBold, 0.4),
    bodyMediumEmphasized = style(14, 20, FontWeight.SemiBold, 0.25),
    bodySmallEmphasized = style(12, 16, FontWeight.SemiBold, 0.4),

    labelLargeEmphasized = style(14, 20, FontWeight.ExtraBold, 0.1),
    labelMediumEmphasized = style(12, 16, FontWeight.ExtraBold, 0.5),
    labelSmallEmphasized = style(11, 16, FontWeight.ExtraBold, 0.5),
)
