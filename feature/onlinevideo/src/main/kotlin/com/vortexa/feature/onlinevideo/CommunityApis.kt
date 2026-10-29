package com.vortexa.feature.onlinevideo

import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Layer (c): community data, which is optional by nature and never fatal. */
data class SponsorSegment(
    val startSeconds: Double,
    val endSeconds: Double,
    val category: String,
)

data class DislikeStats(
    val likes: Long,
    val dislikes: Long,
    val rating: Double,
)

object SponsorBlockParser {

    private val KEPT_CATEGORIES = setOf("sponsor", "selfpromo", "interaction", "intro", "outro")

    /** Malformed or empty payloads produce no segments rather than an error. */
    fun parse(json: String): List<SponsorSegment> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val segment = item.optJSONArray("segment") ?: continue
                if (segment.length() < 2) continue
                val category = item.optString("category", "sponsor")
                if (category !in KEPT_CATEGORIES) continue
                val start = segment.optDouble(0, Double.NaN)
                val end = segment.optDouble(1, Double.NaN)
                if (start.isNaN() || end.isNaN() || end <= start) continue
                add(SponsorSegment(startSeconds = start, endSeconds = end, category = category))
            }
        }.sortedBy { it.startSeconds }
    }
}

object DislikeParser {

    fun parse(json: String): DislikeStats? {
        val object0 = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (object0.has("error")) return null
        return DislikeStats(
            likes = object0.optLong("likes", 0L),
            dislikes = object0.optLong("dislikes", 0L),
            rating = object0.optDouble("rating", 0.0),
        )
    }
}

class SponsorBlockClient(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://sponsor.ajay.app",
) {
    suspend fun segments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/api/skipSegments?videoID=" +
                URLEncoder.encode(videoId, "UTF-8") +
                "&categories=%5B%22sponsor%22%2C%22selfpromo%22%2C%22interaction%22%2C%22intro%22%2C%22outro%22%5D"
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) emptyList() else SponsorBlockParser.parse(body)
            }
        }.getOrDefault(emptyList())
    }
}

class ReturnYouTubeDislikeClient(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://returnyoutubedislikeapi.com",
) {
    suspend fun votes(videoId: String): DislikeStats? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$baseUrl/votes?videoId=" + URLEncoder.encode(videoId, "UTF-8")
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) null else DislikeParser.parse(body)
            }
        }.getOrNull()
    }
}
