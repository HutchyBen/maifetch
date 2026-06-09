package maifetch;

import maifetch.api.MaiTeaClient;
import maifetch.api.Models;
import maifetch.api.Pager;
import maifetch.json.Json;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MaifetchTests {
    private MaifetchTests() {
    }

    public static void main(String[] args) throws Exception {
        testJsonParser();
        testConfigPrecedence();
        testOutputFormatting();
        testApiHeadersAndPagination();
        System.out.println("All maifetch tests passed");
    }

    private static void testJsonParser() {
        Map<?, ?> parsed = (Map<?, ?>) Json.parse("{\"name\":\"Mai\\u0054ea\",\"items\":[1,true,null]}");
        assertEquals("MaiTea", parsed.get("name"), "unicode strings should decode");
        List<?> items = (List<?>) parsed.get("items");
        assertEquals(3, items.size(), "arrays should parse");
        assertEquals(Boolean.TRUE, items.get(1), "booleans should parse");
    }

    private static void testConfigPrecedence() throws Exception {
        File configFile = File.createTempFile("maifetch", ".json");
        configFile.deleteOnExit();
        Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8);
        try {
            writer.write("{\"accessToken\":\"file-token\",\"logoSize\":10,\"scoreCount\":2}");
        } finally {
            writer.close();
        }

        Map<String, String> env = new HashMap<String, String>();
        env.put("MAITEA_TOKEN", "env-token");
        env.put("MAITEA_LOGO_SIZE", "12");
        env.put("MAITEA_SCORE_COUNT", "3");

        Config cli = ConfigLoader.load(new String[]{
                "--config-file", configFile.getAbsolutePath(),
                "--access-token", "cli-token",
                "--logo-size", "0",
                "--score-count", "5"
        }, env);
        assertEquals("cli-token", cli.getAccessToken(), "CLI token should win");
        assertEquals(0, cli.getLogoSize(), "CLI logo size should win");
        assertEquals(5, cli.getScoreCount(), "CLI score count should win");

        Config legacyToken = ConfigLoader.load(new String[]{
                "--config-file", configFile.getAbsolutePath(),
                "-t", "legacy-token"
        }, new HashMap<String, String>());
        assertEquals("legacy-token", legacyToken.getAccessToken(), "legacy -t token shortcut should still work");

        boolean rejected = false;
        try {
            ConfigLoader.load(new String[]{"--score-count", "13"}, new HashMap<String, String>());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected, "missing token and invalid score count should reject config");
    }

    private static void testOutputFormatting() {
        Models.Profile profile = new Models.Profile(
                7,
                "Ｐｅｔｅｒ",
                12345,
                13000,
                88,
                new Models.PlayStats(42, 0, 0, 0),
                null
        );
        Models.TrackInfo song = new Models.TrackInfo(
                5,
                "track",
                new Models.LocalizedName("Spring Song", "jp"),
                new Models.LocalizedName("Artist", "artist")
        );
        List<Models.Play> plays = new ArrayList<Models.Play>();
        plays.add(new Models.Play(
                1,
                1005000,
                "100.5000",
                5,
                1005000,
                "1,005,000",
                null,
                "SSS+",
                1,
                "FC",
                true,
                false,
                false,
                new Models.DifficultyLevel(4, "master", "Master"),
                0,
                song,
                profile
        ));

        String output = OutputFormatter.renderWithoutLogo(profile, plays, 4);
        assertTrue(output.contains("Peter"), "full-width profile name should normalize");
        assertTrue(output.contains("Spring Song"), "recent song should render");
        assertTrue(output.contains("1,005,000"), "score should render");
        assertTrue(output.contains("FC"), "full combo label should render");
    }

    private static void testApiHeadersAndPagination() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.add("/api/v1/profiles", "{\"data\":[{\"id\":1,\"name\":\"p\",\"rating\":100,\"rating_highest\":200,\"level\":3,\"play_stats\":{\"total\":4},\"options\":{\"icon\":{\"png\":\"https://example.test/icon.png\"}}}]}");
        transport.add("/api/v1/plays", "{\"data\":[{\"id\":9,\"achievement\":1000000,\"achievement_formatted\":\"100.0000\",\"track\":1,\"score\":1000000,\"score_formatted\":\"1,000,000\",\"rank\":\"SSS\",\"difficulty_level\":{\"value\":\"expert\"},\"song\":{\"id\":1,\"name\":{\"en\":\"One\"}},\"player\":{\"id\":1,\"name\":\"p\"}}],\"links\":{\"first\":\"https://maitea.app/api/v1/plays\",\"last\":\"https://maitea.app/api/v1/plays?page=2\",\"prev\":null,\"next\":\"https://maitea.app/api/v1/plays?page=2\"},\"meta\":{\"current_page\":1,\"last_page\":2,\"total\":2}}");
        transport.add("/api/v1/plays?page=2", "{\"data\":[{\"id\":10,\"song\":{\"name\":{\"en\":\"Two\"}},\"difficulty_level\":{\"value\":\"basic\"}}],\"links\":{\"first\":\"https://maitea.app/api/v1/plays\",\"last\":\"https://maitea.app/api/v1/plays?page=2\",\"prev\":\"https://maitea.app/api/v1/plays\",\"next\":null},\"meta\":{\"current_page\":2,\"last_page\":2,\"total\":2}}");

        MaiTeaClient client = new MaiTeaClient("token-123", "https://maitea.app", transport);
        List<Models.Profile> profiles = client.getProfiles();
        assertEquals(1, profiles.size(), "profiles should decode from data wrapper");
        assertEquals("Bearer token-123", transport.lastHeaders.get("Authorization"), "bearer token should be sent");
        assertEquals("application/json", transport.lastHeaders.get("Accept"), "accept header should be sent");

        Pager<Models.Play> pager = client.getPlays();
        assertEquals("One", pager.currentPage().get(0).song.name.en, "current page should decode");
        List<Models.Play> next = pager.next();
        assertEquals("Two", next.get(0).song.name.en, "next page should follow absolute link");

        boolean noNext = false;
        try {
            pager.next();
        } catch (Pager.PageDoesNotExist expected) {
            noNext = true;
        }
        assertTrue(noNext, "pager should reject missing next link");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FakeTransport implements MaiTeaClient.Transport {
        private final Map<String, String> responses = new HashMap<String, String>();
        private Map<String, String> lastHeaders = new HashMap<String, String>();

        void add(String path, String body) {
            responses.put(path, body);
        }

        @Override
        public MaiTeaClient.Response get(String url, Map<String, String> headers) {
            lastHeaders = new HashMap<String, String>(headers);
            String path = url.substring("https://maitea.app".length());
            String body = responses.get(path);
            if (body == null) {
                return new MaiTeaClient.Response(404, "{\"message\":\"not found\"}");
            }
            return new MaiTeaClient.Response(200, body);
        }
    }
}
