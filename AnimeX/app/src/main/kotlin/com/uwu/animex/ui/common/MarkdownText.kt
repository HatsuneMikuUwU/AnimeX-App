package com.uwu.animex.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import java.net.URLEncoder

/**
 * Renders AniList forum markdown (same compatibility tweaks as AniHyou):
 * img()/youtube()/spoiler tags → standard markdown.
 */
@Composable
fun MarkdownText(
    markdown: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Markdown(
        content = markdown.orEmpty().formatAniListMarkdown(),
        typography = markdownTypography(text = style),
        modifier = modifier,
        imageTransformer = Coil3ImageTransformerImpl,
    )
}

private val imageRegex = Regex("([iI]mg\\d*%*)\\((.*?)\\)")
private val youtubeRegex = Regex("[yY]outube\\((.*?)\\)")
private val spoilerRegex = Regex("~!(.*?)!~", RegexOption.DOT_MATCHES_ALL)

/** Normalize AniList-specific markdown before rendering. */
fun String.formatAniListMarkdown(): String =
    this
        .replace("~~~", "")
        .replace(imageRegex, "\n![image]($2)\n")
        .replace(youtubeRegex) { m ->
            val url = m.groupValues[1]
            "[$url]($url)"
        }
        .replace(spoilerRegex) { m ->
            val encoded = runCatching { URLEncoder.encode(m.groupValues[1], "UTF-8") }.getOrDefault("…")
            "\n**(spoiler)** ||$encoded||\n"
        }
