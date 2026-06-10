package maifetch

import maifetch.api.MaiTeaClient
import maifetch.api.Models
import maifetch.api.Pager
import maifetch.json.Json
import java.io.File

object MaifetchTests {
    @JvmStatic
    fun main(args: Array<String>) {
        testJsonParser()
        testConfigPrecedence()
        testOutputFormatting()
        testApiHeadersAndPagination()
        println("All maifetch tests passed")
    }

    private fun testJsonParser() {
        val parsed = Json.parse("""{"name":"Mai\u0054ea","items":[1,true,null]}""") as Map<*, *>
        assertEquals("MaiTea", parsed["name"], "unicode strings should decode")
        val items = parsed["items"] as List<*>
        assertEquals(3, items.size, "arrays should parse")
        assertEquals(true, items[1], "booleans should parse")
    }

    private fun testConfigPrecedence() {
        val configFile = File.createTempFile("maifetch", ".json")
        configFile.deleteOnExit()
        configFile.writeText("""{"accessToken":"file-token","logoSize":10,"scoreCount":2}""", Charsets.UTF_8)

        val env = mapOf(
            "MAITEA_TOKEN" to "env-token",
            "MAITEA_LOGO_SIZE" to "12",
            "MAITEA_SCORE_COUNT" to "3",
        )

        val cli = ConfigLoader.load(
            arrayOf(
                "--config-file", configFile.absolutePath,
                "--access-token", "cli-token",
                "--logo-size", "0",
                "--score-count", "5",
            ),
            env,
        )
        assertEquals("cli-token", cli.accessToken, "CLI token should win")
        assertEquals(0, cli.logoSize, "CLI logo size should win")
        assertEquals(5, cli.scoreCount, "CLI score count should win")

        val legacyToken = ConfigLoader.load(
            arrayOf("--config-file", configFile.absolutePath, "-t", "legacy-token"),
            emptyMap(),
        )
        assertEquals("legacy-token", legacyToken.accessToken, "legacy -t token shortcut should still work")

        var rejected = false
        try {
            ConfigLoader.load(arrayOf("--score-count", "13"), emptyMap())
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected, "missing token and invalid score count should reject config")
    }

    private fun testOutputFormatting() {
        val profile = Models.Profile(
            id = 7,
            name = "Ｐｅｔｅｒ",
            rating = 12345,
            ratingHighest = 13000,
            level = 88,
            playStats = Models.PlayStats(total = 42),
        )
        val song = Models.TrackInfo(
            id = 5,
            code = "track",
            name = Models.LocalizedName("Spring Song", "jp"),
            artist = Models.LocalizedName("Artist", "artist"),
        )
        val plays = listOf(
            Models.Play(
                id = 1,
                achievement = 1005000,
                achievementFormatted = "100.5000",
                track = 5,
                score = 1005000,
                scoreFormatted = "1,005,000",
                rank = "SSS+",
                fullCombo = 1,
                fullComboLabel = "FC",
                highScore = true,
                difficultyLevel = Models.DifficultyLevel(4, "master", "Master"),
                song = song,
                player = profile,
            ),
        )

        val output = OutputFormatter.renderWithoutLogo(profile, plays, 4)
        assertTrue(output.contains("Peter"), "full-width profile name should normalize")
        assertTrue(output.contains("Spring Song"), "recent song should render")
        assertTrue(output.contains("1,005,000"), "score should render")
        assertTrue(output.contains("FC"), "full combo label should render")
    }

    private fun testApiHeadersAndPagination() {
        val transport = FakeTransport()
        transport.add(
            "/api/v1/profiles",
            """{"data":[{"id":1,"name":"p","rating":100,"rating_highest":200,"level":3,"play_stats":{"total":4},"options":{"icon":{"png":"https://example.test/icon.png"}}}]}""",
        )
        transport.add(
            "/api/v1/plays",
            """{"data":[{"id":9,"achievement":1000000,"achievement_formatted":"100.0000","track":1,"score":1000000,"score_formatted":"1,000,000","rank":"SSS","difficulty_level":{"value":"expert"},"song":{"id":1,"name":{"en":"One"}},"player":{"id":1,"name":"p"}}],"links":{"first":"https://maitea.app/api/v1/plays","last":"https://maitea.app/api/v1/plays?page=2","prev":null,"next":"https://maitea.app/api/v1/plays?page=2"},"meta":{"current_page":1,"last_page":2,"total":2}}""",
        )
        transport.add(
            "/api/v1/plays?page=2",
            """{"data":[{"id":10,"song":{"name":{"en":"Two"}},"difficulty_level":{"value":"basic"}}],"links":{"first":"https://maitea.app/api/v1/plays","last":"https://maitea.app/api/v1/plays?page=2","prev":"https://maitea.app/api/v1/plays","next":null},"meta":{"current_page":2,"last_page":2,"total":2}}""",
        )

        val client = MaiTeaClient("token-123", "https://maitea.app", transport)
        val profiles = client.getProfiles()
        assertEquals(1, profiles.size, "profiles should decode from data wrapper")
        assertEquals("Bearer token-123", transport.lastHeaders["Authorization"], "bearer token should be sent")
        assertEquals("application/json", transport.lastHeaders["Accept"], "accept header should be sent")

        val pager = client.getPlays()
        assertEquals("One", pager.currentPage()[0].song.name.en, "current page should decode")
        val next = pager.next()
        assertEquals("Two", next[0].song.name.en, "next page should follow absolute link")

        var noNext = false
        try {
            pager.next()
        } catch (_: Pager.PageDoesNotExist) {
            noNext = true
        }
        assertTrue(noNext, "pager should reject missing next link")
    }

    private fun assertEquals(expected: Any?, actual: Any?, message: String) {
        if (expected != actual) {
            throw AssertionError("$message expected <$expected> but was <$actual>")
        }
    }

    private fun assertTrue(condition: Boolean, message: String) {
        if (!condition) throw AssertionError(message)
    }

    private class FakeTransport : MaiTeaClient.Transport {
        private val responses = linkedMapOf<String, String>()
        var lastHeaders: Map<String, String> = emptyMap()
            private set

        fun add(path: String, body: String) {
            responses[path] = body
        }

        override fun get(url: String, headers: Map<String, String>): MaiTeaClient.Response {
            lastHeaders = headers.toMap()
            val path = url.removePrefix("https://maitea.app")
            val body = responses[path] ?: return MaiTeaClient.Response(404, """{"message":"not found"}""")
            return MaiTeaClient.Response(200, body)
        }
    }
}
