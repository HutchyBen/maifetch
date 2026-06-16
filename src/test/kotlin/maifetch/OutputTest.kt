package maifetch

import maifetch.maitea.DifficultyLevel
import maifetch.maitea.LocalizedName
import maifetch.maitea.Play
import maifetch.maitea.PlayStats
import maifetch.maitea.Profile
import maifetch.maitea.TrackInfo
import kotlin.test.Test
import kotlin.test.assertTrue

class OutputTest {
    @Test
    fun `info strings keep profile and recent score content`() {
        val profile = Profile(
            id = 123,
            name = "\uFF34\uFF45\uFF53\uFF54",
            rating = 1500,
            ratingHighest = 1600,
            level = 42,
            playStats = PlayStats(total = 99),
        )
        val play = Play(
            scoreFormatted = "1,000,000",
            achievementFormatted = "100.0000",
            rank = "SSS+",
            fullComboLabel = "FC",
            difficultyLevel = DifficultyLevel(value = "master"),
            song = TrackInfo(name = LocalizedName(en = "Song", jp = "")),
        )

        val lines = createInfoStrings(profile, listOf(play), 1)

        assertTrue(lines.any { it.contains("Test") })
        assertTrue(lines.any { it.contains("ID") && it.contains("123") })
        assertTrue(lines.any { it.contains("1,000,000") && it.contains("100.0000%") })
        assertTrue(lines.any { it.contains("Song") })
    }
}
