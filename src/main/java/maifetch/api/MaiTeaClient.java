package maifetch.api;

import maifetch.json.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MaiTeaClient {
    static final String DEFAULT_BASE_URL = "https://maitea.app";

    private final String accessToken;
    private final String baseUrl;
    private final Transport transport;

    public MaiTeaClient(String accessToken) {
        this(accessToken, DEFAULT_BASE_URL, new UrlConnectionTransport());
    }

    public MaiTeaClient(String accessToken, String baseUrl, Transport transport) {
        this.accessToken = accessToken == null ? "" : accessToken;
        this.baseUrl = trimTrailingSlash(baseUrl == null || baseUrl.trim().isEmpty() ? DEFAULT_BASE_URL : baseUrl);
        this.transport = transport == null ? new UrlConnectionTransport() : transport;
    }

    public List<Models.Profile> getProfiles() throws IOException {
        Object data = getData("/api/v1/profiles");
        return Models.mapList(data, new Models.Mapper<Models.Profile>() {
            @Override
            public Models.Profile map(Map<String, Object> input) {
                return Models.Profile.fromMap(input);
            }
        });
    }

    public List<Models.TrackInfo> getTracks() throws IOException {
        Object data = getData("/api/v1/tracks");
        return Models.mapList(data, new Models.Mapper<Models.TrackInfo>() {
            @Override
            public Models.TrackInfo map(Map<String, Object> input) {
                return Models.TrackInfo.fromMap(input);
            }
        });
    }

    public Models.Status status() throws IOException {
        Object data = getData("/api/v1/status");
        return Models.Status.fromMap(asMap(data));
    }

    public Pager<Models.Play> getPlays() throws IOException {
        return getPager("/api/v1/plays", playMapper());
    }

    public Pager<Models.Play> getAllPlays() throws IOException {
        return getPager("/api/v1/plays/all", playMapper());
    }

    public Pager<Models.Score> getBestScores() throws IOException {
        return getPager("/api/v1/scores", scoreMapper());
    }

    public Pager<Models.Score> getAllBestScores() throws IOException {
        return getPager("/api/v1/scores/all", scoreMapper());
    }

    <T> Page<T> getPage(String pathOrUrl, Models.Mapper<T> mapper) throws IOException {
        Map<String, Object> root = asMap(request(pathOrUrl));
        Object data = root.get("data");
        List<T> items = Models.mapList(data, mapper);
        Map<String, Object> links = asMap(root.get("links"));
        Map<String, Object> meta = asMap(root.get("meta"));
        return new Page<T>(
                this,
                items,
                stringValue(links.get("first")),
                stringValue(links.get("last")),
                nullableString(links.get("prev")),
                nullableString(links.get("next")),
                intValue(meta.get("current_page")),
                intValue(meta.get("last_page")),
                intValue(meta.get("total"))
        );
    }

    private <T> Pager<T> getPager(String path, Models.Mapper<T> mapper) throws IOException {
        return new Pager<T>(getPage(path, mapper), mapper);
    }

    private Object getData(String path) throws IOException {
        Map<String, Object> root = asMap(request(path));
        return root.get("data");
    }

    private Object request(String pathOrUrl) throws IOException {
        String path = normalizePath(pathOrUrl);
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        Response response = transport.get(baseUrl + path, headers);
        if (response.statusCode < 200 || response.statusCode > 299) {
            throw new IOException("MaiTea request failed with HTTP " + response.statusCode);
        }
        return Json.parse(response.body);
    }

    private String normalizePath(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.trim().isEmpty()) {
            return "/";
        }
        String path = pathOrUrl.trim();
        if (path.startsWith(baseUrl)) {
            path = path.substring(baseUrl.length());
        } else if (path.startsWith(DEFAULT_BASE_URL)) {
            path = path.substring(DEFAULT_BASE_URL.length());
        } else if (path.startsWith("http://") || path.startsWith("https://")) {
            try {
                URL url = new URL(path);
                path = url.getPath() + (url.getQuery() == null ? "" : "?" + url.getQuery());
            } catch (IOException error) {
                throw new IllegalArgumentException("invalid page URL: " + pathOrUrl, error);
            }
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static Models.Mapper<Models.Play> playMapper() {
        return new Models.Mapper<Models.Play>() {
            @Override
            public Models.Play map(Map<String, Object> input) {
                return Models.Play.fromMap(input);
            }
        };
    }

    private static Models.Mapper<Models.Score> scoreMapper() {
        return new Models.Mapper<Models.Score>() {
            @Override
            public Models.Score map(Map<String, Object> input) {
                return Models.Score.fromMap(input);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    private static String stringValue(Object value) {
        String string = nullableString(value);
        return string == null ? "" : string;
    }

    private static String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && !((String) value).trim().isEmpty()) {
            return new BigDecimal((String) value).intValue();
        }
        return 0;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public interface Transport {
        Response get(String url, Map<String, String> headers) throws IOException;
    }

    public static final class Response {
        public final int statusCode;
        public final String body;

        public Response(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body == null ? "" : body;
        }
    }

    private static final class UrlConnectionTransport implements Transport {
        @Override
        public Response get(String url, Map<String, String> headers) throws IOException {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }

            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = stream == null ? "" : readAll(stream);
            connection.disconnect();
            return new Response(statusCode, body);
        }

        private static String readAll(InputStream stream) throws IOException {
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                stream.close();
            }
        }
    }
}
