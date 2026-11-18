package com.uwu.animex.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateParserTest {
    @Test
    fun isReleaseNewerComparesSemverAndPrereleaseCorrectly() {
        assertTrue(isReleaseNewer("v1.2.3", "1.2.2"))
        assertTrue(isReleaseNewer("1.3.0", "1.2.9"))
        assertFalse(isReleaseNewer("v1.2.2", "1.2.2"))
        assertFalse(isReleaseNewer("1.2.2-rc1", "1.2.2"))
        assertTrue(isReleaseNewer("1.2.2", "1.2.2-rc2"))
        assertTrue(isReleaseNewer("1.2.3-beta.2", "1.2.3-beta.1"))
    }

    @Test
    fun parseGitHubReleasesJsonToleratesNullFieldsAndSelectsPreferredAbi() {
        val sampleJson =
            """
            [
              {
                "tag_name": "v1.3.0",
                "name": null,
                "body": null,
                "draft": false,
                "prerelease": null,
                "published_at": "2026-11-15T10:20:30Z",
                "html_url": null,
                "assets": [
                  {
                    "name": "AnimeX-1.3.0-universal-release.apk",
                    "browser_download_url": "https://github.com/HatsuneMikuUwU/AnimeX-App/releases/download/v1.3.0/AnimeX-1.3.0-universal-release.apk",
                    "size": 25000000,
                    "digest": null
                  },
                  {
                    "name": "AnimeX-1.3.0-arm64-v8a-release.apk",
                    "browser_download_url": "https://github.com/HatsuneMikuUwU/AnimeX-App/releases/download/v1.3.0/AnimeX-1.3.0-arm64-v8a-release.apk",
                    "size": 18000000,
                    "digest": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                  }
                ]
              },
              {
                "tag_name": "v1.2.5-beta1",
                "name": "Beta",
                "body": "Catatan rilis beta",
                "draft": false,
                "prerelease": true,
                "assets": null
              }
            ]
            """.trimIndent()

        val (latest, older) =
            parseGitHubReleasesJson(
                body = sampleJson,
                allowPrereleases = false,
                abi = "arm64-v8a",
                currentVersion = "1.2.2",
            )

        assertNotNull(latest)
        assertEquals("1.3.0", latest?.tag)
        assertEquals("v1.3.0", latest?.name)
        assertEquals("", latest?.body)
        assertEquals("2026-11-15", latest?.publishedAt)
        assertEquals("AnimeX-1.3.0-arm64-v8a-release.apk", latest?.apkName)
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", latest?.sha256)
        assertEquals("https://github.com/HatsuneMikuUwU/AnimeX-App/releases/tag/v1.3.0", latest?.htmlUrl)
        assertTrue(older.isEmpty())

        val (none, _) =
            parseGitHubReleasesJson(
                body = sampleJson,
                allowPrereleases = true,
                abi = "arm64-v8a",
                currentVersion = "1.3.0",
            )
        assertNull(none)
    }
}
