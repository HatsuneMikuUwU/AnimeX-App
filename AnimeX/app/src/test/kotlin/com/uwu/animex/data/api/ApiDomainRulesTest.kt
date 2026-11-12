package com.uwu.animex.data.api

import com.uwu.animex.core.network.ApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiDomainRulesTest {
    @Test
    fun normalizesSchemeHostAndBasePath() {
        assertEquals("https://api.example.test/", ApiDomainConfig.normalize("http://api.example.test///"))
        assertEquals("https://api.example.test/v2/", ApiDomainConfig.normalize("http://api.example.test/v2/"))
        assertEquals("https://api.example.test/", ApiDomainConfig.normalize(" //api.example.test/ "))
    }

    @Test
    fun rejectsQueryFragmentsAndInvalidDomains() {
        assertThrows(ApiException.Config::class.java) {
            ApiDomainConfig.normalize("https://api.example.test/path?token=secret")
        }
        assertThrows(ApiException.Config::class.java) {
            ApiDomainConfig.normalize("https://api.example.test/path#fragment")
        }
        assertThrows(ApiException.Config::class.java) {
            ApiDomainConfig.normalize("https://user:pass@api.example.test/")
        }
        assertThrows(ApiException.Config::class.java) {
            ApiDomainConfig.normalize("ftp://api.example.test/")
        }
    }

    @Test
    fun selectsOtherDomainOnlyForRetryableFailures() {
        assertEquals(
            "https://alpha.example.test/",
            ApiDomainPolicy.fallbackDomain(
                currentDomain = "https://main.example.test/",
                mainDomain = "https://main.example.test/",
                alphaDomain = "https://alpha.example.test/",
                failure = ApiException.Unreachable(),
            ),
        )
        assertEquals(
            "https://main.example.test/",
            ApiDomainPolicy.fallbackDomain(
                currentDomain = "https://alpha.example.test/",
                mainDomain = "https://main.example.test/",
                alphaDomain = "https://alpha.example.test/",
                failure = ApiException.Http(503),
            ),
        )
        assertNull(
            ApiDomainPolicy.fallbackDomain(
                currentDomain = "https://main.example.test/",
                mainDomain = "https://main.example.test/",
                alphaDomain = "https://alpha.example.test/",
                failure = ApiException.Http(404),
            ),
        )
        assertNull(
            ApiDomainPolicy.fallbackDomain(
                currentDomain = "https://main.example.test/",
                mainDomain = "https://main.example.test/",
                alphaDomain = "",
                failure = ApiException.Timeout(),
            ),
        )
    }

    @Test
    fun restoresMainDomainOnlyAfterCooldown() {
        assertEquals(
            "https://alpha.example.test/",
            ApiDomainPolicy.restoreMainDomain(
                currentDomain = "https://alpha.example.test/",
                mainDomain = "https://main.example.test/",
                alphaDomain = "https://alpha.example.test/",
                fallbackAt = 1_000,
                now = 10_999,
                retryAfterMs = 10_000,
            ),
        )
        assertEquals(
            "https://main.example.test/",
            ApiDomainPolicy.restoreMainDomain(
                currentDomain = "https://alpha.example.test/",
                mainDomain = "https://main.example.test/",
                alphaDomain = "https://alpha.example.test/",
                fallbackAt = 1_000,
                now = 11_000,
                retryAfterMs = 10_000,
            ),
        )
    }

    @Test
    fun treatsOnlyConnectivityAndServerErrorsAsDomainDown() {
        assertEquals(true, ApiDomainPolicy.isDomainDown(ApiException.Unreachable()))
        assertEquals(true, ApiDomainPolicy.isDomainDown(ApiException.Timeout()))
        assertEquals(true, ApiDomainPolicy.isDomainDown(ApiException.Tls()))
        assertEquals(true, ApiDomainPolicy.isDomainDown(ApiException.Http(503)))
        assertEquals(false, ApiDomainPolicy.isDomainDown(ApiException.Http(404)))
        assertEquals(false, ApiDomainPolicy.isDomainDown(ApiException.Remote("x")))
        assertEquals(false, ApiDomainPolicy.isDomainDown(ApiException.Offline()))
    }
}
