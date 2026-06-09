package maifetch;

import maifetch.json.Json;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class ConfigLoader {
    private static final int DEFAULT_LOGO_SIZE = 20;
    private static final int DEFAULT_SCORE_COUNT = 4;

    private ConfigLoader() {
    }

    public static Config load(String[] args) throws IOException {
        return load(args, System.getenv());
    }

    public static Config load(String[] args, Map<String, String> environment) throws IOException {
        ParsedArgs parsed = ParsedArgs.parse(args);
        File configFile = parsed.configFile == null ? defaultConfigFile() : parsed.configFile;

        MutableConfig config = new MutableConfig();
        config.logoSize = DEFAULT_LOGO_SIZE;
        config.scoreCount = DEFAULT_SCORE_COUNT;
        config.configFile = configFile;

        readConfigFile(config, configFile);
        applyEnvironment(config, environment);
        applyArguments(config, parsed);
        validate(config);

        return new Config(config.accessToken, config.configFile, config.logoSize, config.scoreCount);
    }

    static File defaultConfigFile() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.trim().isEmpty()) {
                return new File(appData, "maifetch.json");
            }
            return new File(home, "AppData/Roaming/maifetch.json");
        }
        if (os.contains("mac")) {
            return new File(home, "Library/Application Support/maifetch.json");
        }
        String xdg = System.getenv("XDG_CONFIG_HOME");
        if (xdg != null && !xdg.trim().isEmpty()) {
            return new File(xdg, "maifetch.json");
        }
        return new File(home, ".config/maifetch.json");
    }

    @SuppressWarnings("unchecked")
    private static void readConfigFile(MutableConfig config, File configFile) throws IOException {
        if (configFile == null || !configFile.isFile()) {
            return;
        }
        Object parsed;
        try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            parsed = Json.parse(reader);
        }
        if (!(parsed instanceof Map)) {
            throw new IOException("config file must contain a JSON object");
        }
        Map<String, Object> json = (Map<String, Object>) parsed;
        config.accessToken = stringValue(json.get("accessToken"), config.accessToken);
        config.logoSize = intValue(json.get("logoSize"), config.logoSize);
        config.scoreCount = intValue(json.get("scoreCount"), config.scoreCount);
    }

    private static void applyEnvironment(MutableConfig config, Map<String, String> environment) {
        Map<String, String> env = environment == null ? new HashMap<String, String>() : environment;
        config.accessToken = firstNonBlank(env, config.accessToken, "MAITEA_TOKEN", "MAIFETCH_TOKEN");
        config.logoSize = firstInt(env, config.logoSize, "MAITEA_LOGO_SIZE", "MAIFETCH_LOGO_SIZE");
        config.scoreCount = firstInt(env, config.scoreCount, "MAITEA_SCORE_COUNT", "MAIFETCH_SCORE_COUNT");
        String configPath = firstNonBlank(env, null, "MAITEA_CONFIG_FILE", "MAIFETCH_CONFIG_FILE");
        if (configPath != null) {
            config.configFile = new File(configPath);
        }
    }

    private static void applyArguments(MutableConfig config, ParsedArgs parsed) {
        if (parsed.accessToken != null) {
            config.accessToken = parsed.accessToken;
        }
        if (parsed.logoSize != null) {
            config.logoSize = parsed.logoSize.intValue();
        }
        if (parsed.scoreCount != null) {
            config.scoreCount = parsed.scoreCount.intValue();
        }
        if (parsed.configFile != null) {
            config.configFile = parsed.configFile;
        }
    }

    private static void validate(MutableConfig config) {
        if (config.accessToken == null || config.accessToken.trim().isEmpty()) {
            throw new IllegalArgumentException("access token is required");
        }
        if (config.scoreCount < 1) {
            throw new IllegalArgumentException("score count must be at least 1");
        }
        if (config.scoreCount > 12) {
            throw new IllegalArgumentException("score count cannot be higher than 12");
        }
    }

    private static String firstNonBlank(Map<String, String> env, String fallback, String first, String second) {
        String value = env.get(first);
        if (value == null || value.trim().isEmpty()) {
            value = env.get(second);
        }
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static int firstInt(Map<String, String> env, int fallback, String first, String second) {
        String value = firstNonBlank(env, null, first, second);
        return value == null ? fallback : Integer.parseInt(value);
    }

    private static String stringValue(Object value, String fallback) {
        return value instanceof String ? (String) value : fallback;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && !((String) value).trim().isEmpty()) {
            return Integer.parseInt((String) value);
        }
        return fallback;
    }

    private static final class MutableConfig {
        private String accessToken = "";
        private File configFile;
        private int logoSize;
        private int scoreCount;
    }

    static final class ParsedArgs {
        private String accessToken;
        private File configFile;
        private Integer logoSize;
        private Integer scoreCount;

        static ParsedArgs parse(String[] args) {
            ParsedArgs parsed = new ParsedArgs();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    throw new HelpRequested();
                }
                if (arg.startsWith("--access-token=")) {
                    parsed.accessToken = arg.substring("--access-token=".length());
                } else if ("--access-token".equals(arg) || "-a".equals(arg) || "-t".equals(arg)) {
                    parsed.accessToken = requireValue(args, ++i, arg);
                } else if (arg.startsWith("--logo-size=")) {
                    parsed.logoSize = Integer.valueOf(arg.substring("--logo-size=".length()));
                } else if ("--logo-size".equals(arg) || "-l".equals(arg)) {
                    parsed.logoSize = Integer.valueOf(requireValue(args, ++i, arg));
                } else if (arg.startsWith("--score-count=")) {
                    parsed.scoreCount = Integer.valueOf(arg.substring("--score-count=".length()));
                } else if ("--score-count".equals(arg) || "-s".equals(arg)) {
                    parsed.scoreCount = Integer.valueOf(requireValue(args, ++i, arg));
                } else if (arg.startsWith("--config-file=")) {
                    parsed.configFile = new File(arg.substring("--config-file=".length()));
                } else if ("--config-file".equals(arg) || "-c".equals(arg)) {
                    parsed.configFile = new File(requireValue(args, ++i, arg));
                } else {
                    throw new IllegalArgumentException("unknown argument: " + arg);
                }
            }
            return parsed;
        }

        private static String requireValue(String[] args, int index, String flag) {
            if (index >= args.length) {
                throw new IllegalArgumentException(flag + " requires a value");
            }
            return args[index];
        }
    }

    public static final class HelpRequested extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
