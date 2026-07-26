package uk.co.pcgsoft.tracecapture.data.local.util

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun parseUrlList(jsonString: String?): List<String> {
    if (jsonString.isNullOrBlank()) return emptyList()
    return try {
        json.decodeFromString<List<String>>(jsonString)
    } catch (_: Exception) {
        emptyList()
    }
}

fun toUrlListJson(urls: List<String>): String {
    return json.encodeToString(urls)
}
