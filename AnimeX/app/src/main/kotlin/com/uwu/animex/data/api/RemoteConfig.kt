package com.uwu.animex.data.api

import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

data class RemoteConfig(
    val domainApi: String,
    val domainApiAlpha: String?,
) {
    companion object {
        val EMPTY = RemoteConfig(domainApi = "", domainApiAlpha = null)
    }
}

object RemoteConfigParser {
    fun parse(json: String): RemoteConfig {
        val root =
            try {
                JsonParser.parseString(json)
            } catch (e: RuntimeException) {
                throw JsonParseException("Invalid API config JSON", e)
            }
        if (!root.isJsonObject) throw JsonParseException("API config must be a JSON object")

        val envelope = root.asJsonObject
        envelope.get("status")?.let { status ->
            if (primitiveString(status) != "200") throw JsonParseException("API config returned a non-success status")
        }
        envelope.get("error")?.let { error ->
            if (truthy(error)) throw JsonParseException("API config returned an error")
        }

        val data = envelope.get("data")
        if (data == null || !data.isJsonObject) throw JsonParseException("API config data is missing")
        val entries = data.asJsonObject

        return RemoteConfig(
            domainApi = activeValue(entries.get("domain_api")).orEmpty(),
            domainApiAlpha = activeValue(entries.get("domain_api_alpha")),
        )
    }

    private fun activeValue(element: JsonElement?): String? {
        if (element == null || !element.isJsonObject) return null
        val item = element.asJsonObject
        val status = item
            .get("key_status")
            ?.let(::primitiveString)
            ?.trim()
            .orEmpty()
        if (status.isNotEmpty() && !status.equals("active", ignoreCase = true)) return null
        return item
            .get("value")
            ?.let(::primitiveString)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    private fun primitiveString(element: JsonElement): String? =
        if (element.isJsonPrimitive) runCatching { element.asString }.getOrNull() else null

    private fun truthy(element: JsonElement): Boolean =
        when (primitiveString(element)?.trim()?.lowercase()) {
            null, "", "false", "0", "no" -> false
            else -> true
        }
}
