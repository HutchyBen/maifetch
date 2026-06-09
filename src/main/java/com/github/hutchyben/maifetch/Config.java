package com.github.hutchyben.maifetch;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;

final class Config {
    private static final Gson GSON = new Gson();
    private static final int DEFAULT_LOGO_SIZE = 20;
    private static final int DEFAULT_SCORE_COUNT = 4;

    private String accessToken;
    private String configFile;
    private int scoreCount = DEFAULT_SCORE_COUNT;
    private int logoSize = DEFAULT_LOGO_SIZE;

    String getAccessToken() {
        return accessToken;
    }

    String getConfigFile() {
        return configFile;
    }

    int getScoreCount() {
        return scoreCount;
    }

    int getLogoSize() {
        return logoSize;
    }

    static Config load(String[] args) throws IOException {
        return load(args, System.getenv(), System.getProperty("user.home"), System.getProperty("os.name"));
    }

    static Config load(String[] args, Map<String, String> env, String userHome, String osName) throws IOException {
        CliOptions cli = CliOptions.parse(args);
        if (cli.help) {
            throw new HelpRequestedException();
        }

        Config config = new Config();
        String configPath = firstNonBlank(cli.configFile, env.get("MAITEA_CONFIG_FILE"), env.get("MAIFETCH_CONFIG_FILE"));
        if (configPath == null) {
            configPath = defaultConfigPath(env, userHome, osName);
        }
        config.configFile = configPath;

        Path path = Paths.get(configPath);
        if (Files.isRegularFile(path)) {
            Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
            try {
                RawConfig raw = GSON.fromJson(reader, RawConfig.class);
                if (raw != null) {
                    raw.applyTo(config);
                }
            } finally {
                reader.close();
            }
        }

        overlayEnvironment(config, env);
        cli.applyTo(config);
        config.validate();
        return config;
    }

    static String usage() {
        return "Usage: maifetch [--access-token TOKEN] [--config-file FILE] [--score-count N] [--logo-size N]\n"
            + "\n"
            + "Options:\n"
            + "  -a, -t, --access-token TOKEN   token for your MaiTea account (required)\n"
            + "  -c, --config-file FILE         JSON config file path\n"
            + "  -s, --score-count N            amount of recent scores to display (max 12)\n"
            + "  -l, --logo-size N              ASCII logo size; zero or negative disables it\n"
            + "  -h, --help                     show this help";
    }

    private static void overlayEnvironment(Config config, Map<String, String> env) {
        overlayToken(config, env.get("MAIFETCH_TOKEN"));
        overlayToken(config, env.get("MAITEA_TOKEN"));
        overlayInt(env.get("MAIFETCH_SCORE_COUNT"), new IntConsumer() {
            @Override
            public void accept(int value) {
                config.scoreCount = value;
            }
        });
        overlayInt(env.get("MAITEA_SCORE_COUNT"), new IntConsumer() {
            @Override
            public void accept(int value) {
                config.scoreCount = value;
            }
        });
        overlayInt(env.get("MAIFETCH_LOGO_SIZE"), new IntConsumer() {
            @Override
            public void accept(int value) {
                config.logoSize = value;
            }
        });
        overlayInt(env.get("MAITEA_LOGO_SIZE"), new IntConsumer() {
            @Override
            public void accept(int value) {
                config.logoSize = value;
            }
        });
    }

    private static void overlayToken(Config config, String value) {
        if (isPresent(value)) {
            config.accessToken = value;
        }
    }

    private static void overlayInt(String value, IntConsumer consumer) {
        if (isPresent(value)) {
            consumer.accept(Integer.parseInt(value.trim()));
        }
    }

    private static String defaultConfigPath(Map<String, String> env, String userHome, String osName) {
        String normalized = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (normalized.contains("win")) {
            String appData = env.get("APPDATA");
            return Paths.get(isPresent(appData) ? appData : userHome, "maifetch.json").toString();
        }
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return Paths.get(userHome, "Library", "Application Support", "maifetch.json").toString();
        }
        String xdgConfig = env.get("XDG_CONFIG_HOME");
        if (isPresent(xdgConfig)) {
            return Paths.get(xdgConfig, "maifetch.json").toString();
        }
        return Paths.get(userHome, ".config", "maifetch.json").toString();
    }

    private void validate() {
        if (!isPresent(accessToken)) {
            throw new IllegalArgumentException("access token is required");
        }
        if (scoreCount < 0) {
            throw new IllegalArgumentException("score count cannot be negative");
        }
        if (scoreCount > 12) {
            throw new IllegalArgumentException("score count cannot be higher than 12");
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (isPresent(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.trim().isEmpty();
    }

    static final class HelpRequestedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private interface IntConsumer {
        void accept(int value);
    }

    private static final class RawConfig {
        String accessToken;
        Integer scoreCount;
        Integer logoSize;

        void applyTo(Config config) {
            if (isPresent(accessToken)) {
                config.accessToken = accessToken;
            }
            if (scoreCount != null) {
                config.scoreCount = scoreCount;
            }
            if (logoSize != null) {
                config.logoSize = logoSize;
            }
        }
    }

    private static final class CliOptions {
        String accessToken;
        String configFile;
        Integer scoreCount;
        Integer logoSize;
        boolean help;

        static CliOptions parse(String[] args) {
            CliOptions options = new CliOptions();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    options.help = true;
                } else if (arg.startsWith("--access-token=")) {
                    options.accessToken = valueAfterEquals(arg);
                } else if ("--access-token".equals(arg) || "-a".equals(arg) || "-t".equals(arg)) {
                    options.accessToken = requireValue(args, ++i, arg);
                } else if (arg.startsWith("--config-file=")) {
                    options.configFile = valueAfterEquals(arg);
                } else if ("--config-file".equals(arg) || "-c".equals(arg)) {
                    options.configFile = requireValue(args, ++i, arg);
                } else if (arg.startsWith("--score-count=")) {
                    options.scoreCount = Integer.parseInt(valueAfterEquals(arg));
                } else if ("--score-count".equals(arg) || "-s".equals(arg)) {
                    options.scoreCount = Integer.parseInt(requireValue(args, ++i, arg));
                } else if (arg.startsWith("--logo-size=")) {
                    options.logoSize = Integer.parseInt(valueAfterEquals(arg));
                } else if ("--logo-size".equals(arg) || "-l".equals(arg)) {
                    options.logoSize = Integer.parseInt(requireValue(args, ++i, arg));
                } else {
                    throw new IllegalArgumentException("unknown argument: " + arg);
                }
            }
            return options;
        }

        void applyTo(Config config) {
            if (isPresent(accessToken)) {
                config.accessToken = accessToken;
            }
            if (scoreCount != null) {
                config.scoreCount = scoreCount;
            }
            if (logoSize != null) {
                config.logoSize = logoSize;
            }
        }

        private static String requireValue(String[] args, int index, String flag) {
            if (index >= args.length) {
                throw new IllegalArgumentException("missing value for " + flag);
            }
            return args[index];
        }

        private static String valueAfterEquals(String arg) {
            int index = arg.indexOf('=');
            return index >= 0 ? arg.substring(index + 1) : "";
        }
    }
}
