package maifetch

import maifetch.api.MaiTeaClient
import maifetch.api.Models
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object Maifetch {
    @JvmStatic
    fun main(args: Array<String>) {
        try {
            run(args)
        } catch (_: ConfigLoader.HelpRequested) {
            printHelp()
        } catch (error: Exception) {
            println(error.message)
        }
    }

    fun run(args: Array<String>) {
        val config = ConfigLoader.load(args)
        val client = MaiTeaClient(config.accessToken)
        val profiles = client.getProfiles()
        if (profiles.isEmpty()) {
            println("No profiles found")
            return
        }

        val executor = Executors.newSingleThreadExecutor()
        try {
            val plays = executor.submit<List<Models.Play>> { client.getPlays().currentPage() }
            output(plays.get(30, TimeUnit.SECONDS), profiles.first(), config.logoSize, config.scoreCount)
        } finally {
            executor.shutdownNow()
        }
    }

    fun output(plays: List<Models.Play>, profile: Models.Profile, logoSize: Int, scoreCount: Int) {
        val infoLines = OutputFormatter.createInfoLines(profile, plays, scoreCount)
        if (logoSize > 0) {
            val logoLines = AsciiLogo.fromUrl(profile.options.icon.png, logoSize)
            println(OutputFormatter.renderCombined(infoLines, logoLines, logoSize))
        } else {
            println(OutputFormatter.renderWithoutLogo(profile, plays, scoreCount))
        }
    }

    private fun printHelp() {
        println("Usage: maifetch [options]")
        println("  -a, --access-token TOKEN   token for your MaiTea account")
        println("  -t TOKEN                   legacy token shortcut")
        println("  -l, --logo-size SIZE       ASCII logo size; zero or negative disables it")
        println("  -s, --score-count COUNT    recent scores to display, max 12")
        println("  -c, --config-file FILE     JSON config file")
    }
}
