package maifetch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Serializable
data class MaifetchConfig(
    @SerialName("accessToken") val accessToken: String = "",
    @SerialName("scoreCount") val scoreCount: Int = 4,
    @SerialName("logoSize") val logoSize: Int = 20,
)

data class CliOptions(
    val accessToken: String? = null,
    val scoreCount: Int? = null,
    val logoSize: Int? = null,
    val configFile: Path? = null,
)

private val json = Json { ignoreUnknownKeys = true }

fun loadConfig(args: Array<String>, env: Map<String, String> = System.getenv()): MaifetchConfig {
    val cli = parseCli(args)
    val configPath = cli.configFile ?: env["MAITEA_CONFIG_FILE"]?.let(Paths::get) ?: defaultConfigPath()
    val fileConfig = readConfigFile(configPath)

    val merged = MaifetchConfig(
        accessToken = cli.accessToken
            ?: env["MAITEA_TOKEN"]
            ?: fileConfig.accessToken,
        scoreCount = cli.scoreCount
            ?: env["MAITEA_SCORE_COUNT"]?.toIntOrNull()
            ?: fileConfig.scoreCount,
        logoSize = cli.logoSize
            ?: env["MAITEA_LOGO_SIZE"]?.toIntOrNull()
            ?: fileConfig.logoSize,
    )

    require(merged.accessToken.isNotBlank()) { "access token is required" }
    require(merged.scoreCount <= 12) { "score count cannot be higher than 12" }
    return merged
}

fun parseCli(args: Array<String>): CliOptions {
    var accessToken: String? = null
    var scoreCount: Int? = null
    var logoSize: Int? = null
    var configFile: Path? = null

    var index = 0
    while (index < args.size) {
        val arg = args[index]
        fun nextValue(): String {
            require(index + 1 < args.size) { "missing value for $arg" }
            index += 1
            return args[index]
        }

        when (arg) {
            "--access-token", "-a", "-t" -> accessToken = nextValue()
            "--score-count", "-s" -> scoreCount = nextValue().toInt()
            "--logo-size", "-l" -> logoSize = nextValue().toInt()
            "--config-file", "-c" -> configFile = Paths.get(nextValue())
            "--help", "-h" -> throw HelpRequested()
            else -> error("unknown argument: $arg")
        }
        index += 1
    }

    return CliOptions(accessToken, scoreCount, logoSize, configFile)
}

fun usage(): String = """
    Usage: maifetch [options]

    Options:
      -a, -t, --access-token <token>  Access token for the MaiTea account
      -s, --score-count <count>       Amount of recent scores to view (max 12)
      -l, --logo-size <size>          Size of the ASCII logo (<1 disables)
      -c, --config-file <path>        JSON config file to use
      -h, --help                      Show this help
""".trimIndent()

class HelpRequested : RuntimeException()

private fun readConfigFile(path: Path): MaifetchConfig {
    if (!Files.exists(path)) return MaifetchConfig()
    return Files.newBufferedReader(path).use { json.decodeFromString(MaifetchConfig.serializer(), it.readText()) }
}

private fun defaultConfigPath(): Path {
    val os = System.getProperty("os.name").lowercase()
    val home = Paths.get(System.getProperty("user.home"))
    val base = when {
        os.contains("win") -> System.getenv("APPDATA")?.let(Paths::get) ?: home
        os.contains("mac") -> home.resolve("Library").resolve("Application Support")
        else -> System.getenv("XDG_CONFIG_HOME")?.let(Paths::get) ?: home.resolve(".config")
    }
    return base.resolve("maifetch.json")
}
