package maifetch

import maifetch.maitea.ApiClient
import maifetch.maitea.Play
import maifetch.maitea.Profile
import maifetch.maitea.difficultyString
import maifetch.maitea.rankString
import kotlin.concurrent.thread

fun wideToNormal(value: String): String = value.map { char ->
    val code = char.code
    if (code in 0xFF01..0xFF5E) (code - 0xFEE0).toChar() else char
}.joinToString("")

fun colour(value: String): String = Ansi.fg(value, 72, 184, 200)

fun createInfoStrings(profile: Profile, plays: List<Play>, scoreCount: Int): List<String> {
    val name = wideToNormal(profile.name)
    val scoreLines = mutableListOf<String>()

    for (play in plays.take(scoreCount)) {
        val fullCombo = play.fullComboLabel ?: ""
        scoreLines += "  ${play.song.name.en}  ${difficultyString(play.difficultyLevel.value)}"
        scoreLines += "  ${play.scoreFormatted} ${play.achievementFormatted}% ${rankString(play.rank)} $fullCombo"
        scoreLines += ""
    }

    return listOf(
        colour(name),
        "-".repeat(name.length),
        "${colour("ID")}: ${profile.id}",
        "${colour("Rating")}: ${"%.2f".format(profile.rating / 100.0)} / ${"%.2f".format(profile.ratingHighest / 100.0)}",
        "${colour("Level")}: ${profile.level}",
        "${colour("Total Credits")}: ${profile.playStats.total}",
        "${colour("Recent Scores")}:",
    ) + scoreLines
}

fun printCombined(infoLines: List<String>, logoLines: List<String>, logoSize: Int) {
    val maxLength = maxOf(infoLines.size, logoLines.size)
    val padding = "  "
    for (index in 0 until maxLength) {
        val logo = if (index < logoLines.size - 1) logoLines[index] else " ".repeat(logoSize * 2)
        val info = if (index < infoLines.size - 1) infoLines[index] else ""
        println("$logo $padding $info")
    }
}

fun output(plays: List<Play>, profile: Profile, logoSize: Int, scoreCount: Int) {
    val infoLines = createInfoStrings(profile, plays, scoreCount)
    if (logoSize > 0) {
        val logo = urlToAscii(profile.options.icon.png, logoSize).split("\n")
        printCombined(infoLines, logo, logoSize)
    } else {
        println(infoLines.joinToString("\n"))
    }
}

fun main(args: Array<String>) {
    val config = try {
        loadConfig(args)
    } catch (_: HelpRequested) {
        println(usage())
        return
    } catch (error: Throwable) {
        println(error.message)
        return
    }

    val client = ApiClient(config.accessToken)
    val profiles = try {
        client.getProfiles()
    } catch (error: Throwable) {
        println(error.message)
        return
    }

    if (profiles.isEmpty()) {
        println("No profiles found")
        return
    }

    var plays: List<Play>? = null
    var failure: Throwable? = null
    val worker = thread(start = true) {
        try {
            plays = client.getPlays().currentPage()
        } catch (error: Throwable) {
            failure = error
        }
    }

    print("Loading...")
    worker.join(30_000)
    println()
    if (worker.isAlive) {
        println("API timed out")
        return
    }
    failure?.let {
        println(it.message)
        return
    }

    output(plays.orEmpty(), profiles[0], config.logoSize, config.scoreCount)
}
