package maifetch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigTest {
    @Test
    fun `cli values override environment`() {
        val config = loadConfig(
            arrayOf("--access-token", "cli-token", "--score-count", "6", "--logo-size", "0"),
            mapOf(
                "MAITEA_TOKEN" to "env-token",
                "MAITEA_SCORE_COUNT" to "3",
                "MAITEA_LOGO_SIZE" to "20",
            ),
        )

        assertEquals("cli-token", config.accessToken)
        assertEquals(6, config.scoreCount)
        assertEquals(0, config.logoSize)
    }

    @Test
    fun `score count remains capped at twelve`() {
        val error = assertFailsWith<IllegalArgumentException> {
            loadConfig(arrayOf("--access-token", "token", "--score-count", "13"), emptyMap())
        }

        assertEquals("score count cannot be higher than 12", error.message)
    }

    @Test
    fun `access token is required`() {
        val error = assertFailsWith<IllegalArgumentException> {
            loadConfig(emptyArray(), emptyMap())
        }

        assertEquals("access token is required", error.message)
    }
}
