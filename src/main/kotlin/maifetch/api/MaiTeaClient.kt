package maifetch.api

import maifetch.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class MaiTeaClient(
    private val accessToken: String,
    baseUrl: String = DEFAULT_BASE_URL,
    private val transport: Transport = UrlConnectionTransport(),
) {
    private val baseUrl: String = trimTrailingSlash(baseUrl.takeUnless { it.isBlank() } ?: DEFAULT_BASE_URL)

    fun getProfiles(): List<Models.Profile> =
        Models.mapList(getData("/api/v1/profiles"), Models.Mapper { Models.Profile.fromMap(it) })

    fun getTracks(): List<Models.TrackInfo> =
        Models.mapList(getData("/api/v1/tracks"), Models.Mapper { Models.TrackInfo.fromMap(it) })

    fun status(): Models.Status = Models.Status.fromMap(asMap(getData("/api/v1/status")))

    fun getPlays(): Pager<Models.Play> = getPager("/api/v1/plays", Models.Mapper { Models.Play.fromMap(it) })

    fun getAllPlays(): Pager<Models.Play> = getPager("/api/v1/plays/all", Models.Mapper { Models.Play.fromMap(it) })

    fun getBestScores(): Pager<Models.Score> = getPager("/api/v1/scores", Models.Mapper { Models.Score.fromMap(it) })

    fun getAllBestScores(): Pager<Models.Score> = getPager("/api/v1/scores/all", Models.Mapper { Models.Score.fromMap(it) })

    internal fun <T> getPage(pathOrUrl: String, mapper: Models.Mapper<T>): Page<T> {
        val root = asMap(request(pathOrUrl))
        val items = Models.mapList(root["data"], mapper)
        val links = asMap(root["links"])
        val meta = asMap(root["meta"])
        return Page(
            client = this,
            data = items,
            first = stringValue(links["first"]),
            last = stringValue(links["last"]),
            previous = nullableString(links["prev"]),
            next = nullableString(links["next"]),
            currentPage = intValue(meta["current_page"]),
            lastPage = intValue(meta["last_page"]),
            total = intValue(meta["total"]),
        )
    }

    private fun <T> getPager(path: String, mapper: Models.Mapper<T>): Pager<T> = Pager(getPage(path, mapper), mapper)

    private fun getData(path: String): Any? = asMap(request(path))["data"]

    private fun request(pathOrUrl: String): Any? {
        val path = normalizePath(pathOrUrl)
        val headers = linkedMapOf(
            "Authorization" to "Bearer $accessToken",
            "Content-Type" to "application/json",
            "Accept" to "application/json",
        )
        val response = transport.get(baseUrl + path, headers)
        if (response.statusCode !in 200..299) {
            throw java.io.IOException("MaiTea request failed with HTTP ${response.statusCode}")
        }
        return Json.parse(response.body)
    }

    private fun normalizePath(pathOrUrl: String?): String {
        if (pathOrUrl.isNullOrBlank()) return "/"
        var path = pathOrUrl.trim()
        path = when {
            path.startsWith(baseUrl) -> path.substring(baseUrl.length)
            path.startsWith(DEFAULT_BASE_URL) -> path.substring(DEFAULT_BASE_URL.length)
            path.startsWith("http://") || path.startsWith("https://") -> {
                val url = URL(path)
                url.path + (url.query?.let { "?$it" } ?: "")
            }
            else -> path
        }
        return if (path.startsWith("/")) path else "/$path"
    }

    interface Transport {
        fun get(url: String, headers: Map<String, String>): Response
    }

    data class Response(
        val statusCode: Int,
        val body: String = "",
    )

    private class UrlConnectionTransport : Transport {
        override fun get(url: String, headers: Map<String, String>): Response {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }

            val statusCode = connection.responseCode
            val stream = if (statusCode >= 400) connection.errorStream else connection.inputStream
            val body = stream?.use { readAll(it) }.orEmpty()
            connection.disconnect()
            return Response(statusCode, body)
        }

        private fun readAll(stream: InputStream): String {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
            }
            return String(output.toByteArray(), StandardCharsets.UTF_8)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://maitea.app"

        @Suppress("UNCHECKED_CAST")
        private fun asMap(value: Any?): Map<String, Any?> = value as? Map<String, Any?> ?: emptyMap()

        private fun stringValue(value: Any?): String = nullableString(value).orEmpty()

        private fun nullableString(value: Any?): String? = value?.toString()

        private fun intValue(value: Any?): Int =
            when (value) {
                is Number -> value.toInt()
                is String -> value.takeIf { it.isNotBlank() }?.let { BigDecimal(it).toInt() } ?: 0
                else -> 0
            }

        private fun trimTrailingSlash(value: String): String = value.removeSuffix("/")
    }
}
