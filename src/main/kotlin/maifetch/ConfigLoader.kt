package maifetch

import maifetch.json.Json
import java.io.File
import java.nio.charset.StandardCharsets

object ConfigLoader {
    private const val DEFAULT_LOGO_SIZE = 20
    private const val DEFAULT_SCORE_COUNT = 4

    fun load(args: Array<String>): Config = load(args, System.getenv())

    fun load(args: Array<String>, environment: Map<String, String>): Config {
        val parsed = ParsedArgs.parse(args)
        val mutable = MutableConfig(
            configFile = parsed.configFile ?: defaultConfigFile(),
            logoSize = DEFAULT_LOGO_SIZE,
            scoreCount = DEFAULT_SCORE_COUNT,
        )

        readConfigFile(mutable)
        applyEnvironment(mutable, environment)
        applyArguments(mutable, parsed)
        validate(mutable)

        return Config(
            accessToken = mutable.accessToken.orEmpty(),
            configFile = mutable.configFile,
            logoSize = mutable.logoSize,
            scoreCount = mutable.scoreCount,
        )
    }

    fun defaultConfigFile(): File {
        val os = System.getProperty("os.name", "").lowercase()
        val home = System.getProperty("user.home", ".")
        if ("win" in os) {
            val appData = System.getenv("APPDATA")
            return if (!appData.isNullOrBlank()) File(appData, "maifetch.json") else File(home, "AppData/Roaming/maifetch.json")
        }
        if ("mac" in os) {
            return File(home, "Library/Application Support/maifetch.json")
        }
        val xdg = System.getenv("XDG_CONFIG_HOME")
        return if (!xdg.isNullOrBlank()) File(xdg, "maifetch.json") else File(home, ".config/maifetch.json")
    }

    @Suppress("UNCHECKED_CAST")
    private fun readConfigFile(config: MutableConfig) {
        val file = config.configFile ?: return
        if (!file.isFile) return
        val parsed = file.inputStream().reader(StandardCharsets.UTF_8).use { Json.parse(it) }
        val json = parsed as? Map<String, Any?> ?: throw java.io.IOException("config file must contain a JSON object")
        config.accessToken = stringValue(json["accessToken"], config.accessToken)
        config.logoSize = intValue(json["logoSize"], config.logoSize)
        config.scoreCount = intValue(json["scoreCount"], config.scoreCount)
    }

    private fun applyEnvironment(config: MutableConfig, environment: Map<String, String>?) {
        val env = environment.orEmpty()
        config.accessToken = firstNonBlank(env, config.accessToken, "MAITEA_TOKEN", "MAIFETCH_TOKEN")
        config.logoSize = firstInt(env, config.logoSize, "MAITEA_LOGO_SIZE", "MAIFETCH_LOGO_SIZE")
        config.scoreCount = firstInt(env, config.scoreCount, "MAITEA_SCORE_COUNT", "MAIFETCH_SCORE_COUNT")
        firstNonBlank(env, null, "MAITEA_CONFIG_FILE", "MAIFETCH_CONFIG_FILE")?.let {
            config.configFile = File(it)
        }
    }

    private fun applyArguments(config: MutableConfig, parsed: ParsedArgs) {
        parsed.accessToken?.let { config.accessToken = it }
        parsed.logoSize?.let { config.logoSize = it }
        parsed.scoreCount?.let { config.scoreCount = it }
        parsed.configFile?.let { config.configFile = it }
    }

    private fun validate(config: MutableConfig) {
        require(!config.accessToken.isNullOrBlank()) { "access token is required" }
        require(config.scoreCount >= 1) { "score count must be at least 1" }
        require(config.scoreCount <= 12) { "score count cannot be higher than 12" }
    }

    private fun firstNonBlank(env: Map<String, String>, fallback: String?, first: String, second: String): String? {
        val value = env[first].takeUnless { it.isNullOrBlank() } ?: env[second].takeUnless { it.isNullOrBlank() }
        return value ?: fallback
    }

    private fun firstInt(env: Map<String, String>, fallback: Int, first: String, second: String): Int =
        firstNonBlank(env, null, first, second)?.toInt() ?: fallback

    private fun stringValue(value: Any?, fallback: String?): String? = (value as? String) ?: fallback

    private fun intValue(value: Any?, fallback: Int): Int =
        when (value) {
            is Number -> value.toInt()
            is String -> value.takeIf { it.isNotBlank() }?.toInt() ?: fallback
            else -> fallback
        }

    private data class MutableConfig(
        var accessToken: String? = "",
        var configFile: File? = null,
        var logoSize: Int = 0,
        var scoreCount: Int = 0,
    )

    data class ParsedArgs(
        var accessToken: String? = null,
        var configFile: File? = null,
        var logoSize: Int? = null,
        var scoreCount: Int? = null,
    ) {
        companion object {
            fun parse(args: Array<String>): ParsedArgs {
                val parsed = ParsedArgs()
                var index = 0
                while (index < args.size) {
                    val arg = args[index]
                    when {
                        arg == "--help" || arg == "-h" -> throw HelpRequested()
                        arg.startsWith("--access-token=") -> parsed.accessToken = arg.substringAfter("=")
                        arg == "--access-token" || arg == "-a" || arg == "-t" -> parsed.accessToken = requireValue(args, ++index, arg)
                        arg.startsWith("--logo-size=") -> parsed.logoSize = arg.substringAfter("=").toInt()
                        arg == "--logo-size" || arg == "-l" -> parsed.logoSize = requireValue(args, ++index, arg).toInt()
                        arg.startsWith("--score-count=") -> parsed.scoreCount = arg.substringAfter("=").toInt()
                        arg == "--score-count" || arg == "-s" -> parsed.scoreCount = requireValue(args, ++index, arg).toInt()
                        arg.startsWith("--config-file=") -> parsed.configFile = File(arg.substringAfter("="))
                        arg == "--config-file" || arg == "-c" -> parsed.configFile = File(requireValue(args, ++index, arg))
                        else -> throw IllegalArgumentException("unknown argument: $arg")
                    }
                    index++
                }
                return parsed
            }

            private fun requireValue(args: Array<String>, index: Int, flag: String): String {
                if (index >= args.size) {
                    throw IllegalArgumentException("$flag requires a value")
                }
                return args[index]
            }
        }
    }

    class HelpRequested : RuntimeException()
}
