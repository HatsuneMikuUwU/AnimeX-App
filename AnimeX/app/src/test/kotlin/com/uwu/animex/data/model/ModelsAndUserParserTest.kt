package com.uwu.animex.data.model

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.uwu.animex.data.user.Discussion
import com.uwu.animex.data.user.unmaskAccountSecret
import com.uwu.animex.data.user.parseUserApiList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsAndUserParserTest {
    private val gson = Gson()

    @Test
    fun trailerYoutubeUrlNormalizesIdsAndSchemelessLinks() {
        assertNull(Trailer(url_youtube = null).youtubeUrl)
        assertNull(Trailer(url_youtube = "   ").youtubeUrl)
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            Trailer(url_youtube = "dQw4w9WgXcQ").youtubeUrl,
        )
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            Trailer(url_youtube = "http://youtu.be/dQw4w9WgXcQ").youtubeUrl,
        )
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            Trailer(url_youtube = "//www.youtube.com/watch?v=dQw4w9WgXcQ").youtubeUrl,
        )
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            Trailer(url_youtube = "youtu.be/dQw4w9WgXcQ").youtubeUrl,
        )
        assertEquals(
            "https://youtube.com/watch?v=dQw4w9WgXcQ",
            Trailer(url_youtube = "youtube.com/watch?v=dQw4w9WgXcQ").youtubeUrl,
        )
    }

    @Test
    fun episodeIndexExtractsNumericIndexCleanly() {
        assertNull((null as String?).toEpisodeIndex())
        assertNull("   ".toEpisodeIndex())
        assertEquals("12", "Episode 12".toEpisodeIndex())
        assertEquals("12.5", "Season 2 - Ep 12.5".toEpisodeIndex())
        assertEquals("OVA", "OVA".toEpisodeIndex())
    }

    @Test
    fun unmaskAccountSecretRoundTripsAndRejectsMalformedHex() {
        val salt = "com.uwu.animex:v1"
        val key = salt.toByteArray(Charsets.UTF_8)
        val plain = "animex_shared_user"
        val masked =
            plain
                .toByteArray(Charsets.UTF_8)
                .mapIndexed { i, b -> "%02x".format((b.toInt() xor key[i % key.size].toInt()) and 0xff) }
                .joinToString("")

        assertEquals(plain, unmaskAccountSecret(masked, salt))
        assertEquals("", unmaskAccountSecret("", salt))
        assertEquals("", unmaskAccountSecret("abc", salt))
        assertEquals("", unmaskAccountSecret("zzzz", salt))
    }

    @Test
    fun parseUserApiListHandlesJsonNullFieldsWithoutThrowing() {
        val jsonWithNulls =
            """
            {
              "status": 200,
              "data": {
                "list": null,
                "comment": null,
                "items": [
                  { "id": "c1", "text": "Seru banget!" }
                ]
              },
              "list": null
            }
            """.trimIndent()
        val root = JsonParser.parseString(jsonWithNulls).asJsonObject
        val items = parseUserApiList(gson, root, Discussion::class.java)
        assertEquals(1, items.size)
        assertEquals("c1", items.first().id)

        val allNullJson = """{"data":{"list":null,"comment":null},"list":null}"""
        val emptyItems = parseUserApiList(gson, JsonParser.parseString(allNullJson).asJsonObject, Discussion::class.java)
        assertTrue(emptyItems.isEmpty())
    }
}
