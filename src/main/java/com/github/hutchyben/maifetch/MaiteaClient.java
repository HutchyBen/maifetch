package com.github.hutchyben.maifetch;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class MaiteaClient {
    private static final Gson GSON = new Gson();
    private static final String DEFAULT_BASE_URL = "https://maitea.app";

    private final String accessToken;
    private final String baseUrl;

    public MaiteaClient(String accessToken) {
        this(accessToken, DEFAULT_BASE_URL);
    }

    MaiteaClient(String accessToken, String baseUrl) {
        this.accessToken = accessToken;
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    public List<MaiteaModels.Profile> getProfiles() throws IOException {
        Type type = new TypeToken<MaiteaModels.Envelope<List<MaiteaModels.Profile>>>() {
        }.getType();
        MaiteaModels.Envelope<List<MaiteaModels.Profile>> envelope = GSON.fromJson(get("/api/v1/profiles"), type);
        return envelope.data;
    }

    public List<MaiteaModels.TrackInfo> getTracks() throws IOException {
        Type type = new TypeToken<MaiteaModels.Envelope<List<MaiteaModels.TrackInfo>>>() {
        }.getType();
        MaiteaModels.Envelope<List<MaiteaModels.TrackInfo>> envelope = GSON.fromJson(get("/api/v1/tracks"), type);
        return envelope.data;
    }

    public Pager<List<MaiteaModels.Play>> getPlays() throws IOException {
        Type type = new TypeToken<MaiteaModels.Page<List<MaiteaModels.Play>>>() {
        }.getType();
        return new Pager<List<MaiteaModels.Play>>(this, type, getPage("/api/v1/plays", type));
    }

    public Pager<List<MaiteaModels.Play>> getAllPlays() throws IOException {
        Type type = new TypeToken<MaiteaModels.Page<List<MaiteaModels.Play>>>() {
        }.getType();
        return new Pager<List<MaiteaModels.Play>>(this, type, getPage("/api/v1/plays/all", type));
    }

    public Pager<List<MaiteaModels.Score>> getBestScores() throws IOException {
        Type type = new TypeToken<MaiteaModels.Page<List<MaiteaModels.Score>>>() {
        }.getType();
        return new Pager<List<MaiteaModels.Score>>(this, type, getPage("/api/v1/scores", type));
    }

    public Pager<List<MaiteaModels.Score>> getAllBestScores() throws IOException {
        Type type = new TypeToken<MaiteaModels.Page<List<MaiteaModels.Score>>>() {
        }.getType();
        return new Pager<List<MaiteaModels.Score>>(this, type, getPage("/api/v1/scores/all", type));
    }

    public MaiteaModels.Status status() throws IOException {
        return GSON.fromJson(get("/api/status"), MaiteaModels.Status.class);
    }

    <T> MaiteaModels.Page<T> getPage(String pathOrUrl, Type type) throws IOException {
        return GSON.fromJson(get(pathOrUrl), type);
    }

    String get(String pathOrUrl) throws IOException {
        String url = pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")
            ? pathOrUrl
            : baseUrl + pathOrUrl;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String body = readFully(stream);
        if (status < 200 || status >= 300) {
            throw new IOException("MaiTea API returned HTTP " + status + (body.isEmpty() ? "" : ": " + body));
        }
        return body;
    }

    private static String readFully(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        try {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
            return builder.toString();
        } finally {
            reader.close();
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
