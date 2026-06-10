package maifetch

import maifetch.api.Models

object OutputFormatter {
    fun createInfoLines(profile: Models.Profile, plays: List<Models.Play>, scoreCount: Int): List<String> {
        val name = wideToNormal(profile.name)
        val lines = mutableListOf<String>()
        lines += color(name)
        lines += "-".repeat(name.length)
        lines += "${color("ID")}: ${profile.id}"
        lines += "%s: %.2f / %.2f".format(color("Rating"), profile.rating / 100.0, profile.ratingHighest / 100.0)
        lines += "${color("Level")}: ${profile.level}"
        lines += "${color("Total Credits")}: ${profile.playStats.total}"
        lines += "${color("Recent Scores")}:"

        plays.take(scoreCount).forEach { play ->
            val fullCombo = play.fullComboLabel.orEmpty()
            lines += "  ${play.song.name.en}  ${difficultyString(play.difficultyLevel.value)}"
            lines += "  ${play.scoreFormatted} ${play.achievementFormatted}% ${rankString(play.rank)} $fullCombo"
            lines += ""
        }
        return lines
    }

    fun renderWithoutLogo(profile: Models.Profile, plays: List<Models.Play>, scoreCount: Int): String =
        createInfoLines(profile, plays, scoreCount).joinToString(System.lineSeparator())

    fun renderCombined(infoLines: List<String>, logoLines: List<String>, logoSize: Int): String {
        val maxLength = maxOf(infoLines.size, logoLines.size)
        val blankLogo = " ".repeat(maxOf(0, logoSize * 2))
        return (0 until maxLength).joinToString(System.lineSeparator()) { index ->
            val logo = logoLines.getOrElse(index) { blankLogo }
            val info = infoLines.getOrElse(index) { "" }
            "$logo   $info"
        }
    }

    fun wideToNormal(value: String?): String =
        value.orEmpty().map { ch ->
            when {
                ch == '\u3000' -> ' '
                ch in '\uFF01'..'\uFF5E' -> (ch.code - 0xFEE0).toChar()
                else -> ch
            }
        }.joinToString("")

    fun color(text: String): String = Ansi.foreground(text, 72, 184, 200)

    fun difficultyString(difficulty: String?): String =
        when (difficulty) {
            "easy" -> Ansi.foregroundBackground("Easy", 255, 255, 255, 69, 174, 255)
            "basic" -> Ansi.foregroundBackground("Basic", 255, 255, 255, 111, 212, 61)
            "advanced" -> Ansi.foregroundBackground("Advanced", 255, 255, 255, 248, 183, 9)
            "expert" -> Ansi.foregroundBackground("Expert", 255, 255, 255, 255, 46, 66)
            "master" -> Ansi.foregroundBackground("Master", 255, 255, 255, 171, 140, 233)
            "remaster", "re:master" -> Ansi.foregroundBackground("Re:Master", 255, 255, 255, 207, 114, 237)
            "utage" -> Ansi.foregroundBackground("Utage", 255, 255, 255, 255, 68, 1)
            else -> difficulty.orEmpty()
        }

    fun rankString(rank: String?): String =
        when (rank) {
            "SSS+" -> Ansi.foreground("S", 255, 200, 54) +
                Ansi.foreground("S", 225, 38, 165) +
                Ansi.foreground("S", 73, 64, 233) +
                Ansi.foreground("+", 21, 203, 148)
            "SSS" -> Ansi.foreground("S", 255, 200, 54) +
                Ansi.foreground("S", 232, 39, 148) +
                Ansi.foreground("S", 18, 195, 144)
            "SS+", "SS" -> Ansi.foregroundBackground(rank, 248, 200, 75, 143, 71, 33)
            "S+", "S" -> Ansi.foregroundBackground(rank, 248, 200, 75, 75, 82, 82)
            "AAA", "AA", "A" -> Ansi.foreground(rank, 23, 163, 255)
            else -> rank.orEmpty()
        }
}
