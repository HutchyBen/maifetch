package com.github.hutchyben.maifetch;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ConfigTest {
    @Test
    public void cliOverridesEnvironmentAndConfigFile() throws Exception {
        Path directory = Files.createTempDirectory("maifetch-config");
        Path configFile = directory.resolve("maifetch.json");
        Files.write(configFile, "{\"accessToken\":\"file-token\",\"scoreCount\":1,\"logoSize\":12}".getBytes(StandardCharsets.UTF_8));

        Map<String, String> env = new HashMap<String, String>();
        env.put("MAIFETCH_TOKEN", "legacy-env-token");
        env.put("MAITEA_TOKEN", "env-token");
        env.put("MAITEA_SCORE_COUNT", "5");
        env.put("MAITEA_LOGO_SIZE", "8");

        Config config = Config.load(new String[]{
            "--config-file", configFile.toString(),
            "--access-token", "cli-token",
            "--score-count", "2",
            "--logo-size", "0"
        }, env, directory.toString(), "Linux");

        assertEquals("cli-token", config.getAccessToken());
        assertEquals(2, config.getScoreCount());
        assertEquals(0, config.getLogoSize());
    }

    @Test
    public void documentedMaiteaEnvironmentOverridesLegacyMaifetchEnvironment() throws Exception {
        Path directory = Files.createTempDirectory("maifetch-config");
        Map<String, String> env = new HashMap<String, String>();
        env.put("MAIFETCH_TOKEN", "legacy-token");
        env.put("MAITEA_TOKEN", "documented-token");

        Config config = Config.load(new String[0], env, directory.toString(), "Linux");

        assertEquals("documented-token", config.getAccessToken());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTooManyScores() throws Exception {
        Path directory = Files.createTempDirectory("maifetch-config");
        Map<String, String> env = new HashMap<String, String>();
        env.put("MAITEA_TOKEN", "token");

        Config.load(new String[]{"--score-count", "13"}, env, directory.toString(), "Linux");
    }
}
