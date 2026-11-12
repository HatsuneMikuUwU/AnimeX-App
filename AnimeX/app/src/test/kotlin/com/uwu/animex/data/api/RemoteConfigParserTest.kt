package com.uwu.animex.data.api

import com.google.gson.JsonParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteConfigParserTest {
    @Test
    fun parsesOnlyActiveApiDomainsFromProvidedEnvelope() {
        val config = RemoteConfigParser.parse(
            """
            {
              "status": 200,
              "error": false,
              "data": {
                "ads": {"id": "5", "code": "ADS_BANNER_FLAG_X", "value": "banner-value", "key_status": "active"},
                "ads_video_ctr": {"id": "7", "code": "ADS_INTERSTISIAL_VIDEO_CTR", "value": "10", "key_status": "active"},
                "domain_api": {"id": "17", "code": "DOMAIN_API", "value": "https://xyz-api.animein.net/", "key_status": "active"},
                "domain_api_alpha": {"id": "26", "code": "DOMAIN_API_ALPHA", "value": "https://alpha-api.animein.net/", "key_status": "active"},
                "ads_source": {"id": "18", "code": "ADS_SOURCE", "value": "LOKLOK_COMBINE", "key_status": "active"}
              }
            }
            """.trimIndent(),
        )

        assertEquals("https://xyz-api.animein.net/", config.domainApi)
        assertEquals("https://alpha-api.animein.net/", config.domainApiAlpha)
    }

    @Test
    fun ignoresInactiveDomainsAndAcceptsNumericEnvelopeStatus() {
        val config = RemoteConfigParser.parse(
            """
            {
              "status": 200,
              "error": false,
              "data": {
                "domain_api": {"value": "https://api.example.test", "key_status": "inactive"},
                "domain_api_alpha": {"value": "https://alpha.example.test", "key_status": "ACTIVE"},
                "ads_video_ctr": {"value": 12, "key_status": "active"}
              }
            }
            """.trimIndent(),
        )

        assertEquals("", config.domainApi)
        assertEquals("https://alpha.example.test", config.domainApiAlpha)
    }

    @Test
    fun rejectsNonSuccessOrMalformedEnvelopes() {
        assertThrows(JsonParseException::class.java) {
            RemoteConfigParser.parse("{\"status\":500,\"error\":false,\"data\":{}}")
        }
        assertThrows(JsonParseException::class.java) {
            RemoteConfigParser.parse("{\"status\":200,\"error\":true,\"data\":{}}")
        }
        assertThrows(JsonParseException::class.java) {
            RemoteConfigParser.parse("{\"status\":200,\"error\":false}")
        }
        assertThrows(JsonParseException::class.java) {
            RemoteConfigParser.parse("not-json")
        }
    }
}
