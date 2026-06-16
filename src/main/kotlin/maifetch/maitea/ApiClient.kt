package maifetch.maitea

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.HttpURLConnection

private const val BASE_URL = "https://maitea.app"

private val json = Json { ignoreUnknownKeys = true }

class ApiClient(
    private val accessToken: String,
) {
    fun getProfiles(): List<Profile> {
        val body = get("/api/v1/profiles")
        return json.decodeFromString(DataEnvelope.serializer(ListSerializer(Profile.serializer())), body).data
    }

    fun getTracks(): List<TrackInfo> {
        val body = get("/api/v1/tracks")
        return json.decodeFromString(DataEnvelope.serializer(ListSerializer(TrackInfo.serializer())), body).data
    }

    fun getPlays(): Pager<List<Play>> {
        val serializer = ListSerializer(Play.serializer())
        val page = getPage("/api/v1/plays", serializer)
        return Pager(this, page, serializer)
    }

    fun getAllPlays(): Pager<List<Play>> {
        val serializer = ListSerializer(Play.serializer())
        val page = getPage("/api/v1/plays/all", serializer)
        return Pager(this, page, serializer)
    }

    internal fun <T> getPage(url: String, serializer: KSerializer<T>): PagerPage<T> {
        val path = url.removePrefix(BASE_URL)
        val body = get(path)
        return json.decodeFromString(PagerPage.serializer(serializer), body)
    }

    private fun get(path: String): String {
        val connection = URI.create(BASE_URL + path).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000

        if (connection.responseCode !in 200..299) {
            error("MaiTea API returned HTTP ${connection.responseCode}")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}
