package com.github.hutchyben.maifetch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class MaiteaClientTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> authorization = new AtomicReference<String>();

    @Before
    public void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/api/v1/profiles", new JsonHandler("{\"data\":[{\"id\":7,\"name\":\"ＭＡＩ\",\"rating\":10000,\"rating_highest\":12000,\"level\":10,\"play_stats\":{\"total\":44},\"options\":{\"icon\":{\"id\":1,\"png\":\"http://example.test/icon.png\",\"webp\":\"\"}}}]}"));
        server.createContext("/api/v1/plays", new JsonHandler("{\"data\":[{\"id\":9,\"achievement_formatted\":\"99.0000\",\"score_formatted\":\"999,999\",\"rank\":\"SS\",\"full_combo_label\":null,\"difficulty_level\":{\"key\":4,\"value\":\"master\",\"label\":\"Master\"},\"song\":{\"id\":2,\"code\":\"abc\",\"name\":{\"en\":\"Track\",\"jp\":\"\"},\"artist\":{\"en\":\"Artist\",\"jp\":\"\"}}}],\"links\":{\"first\":\"/api/v1/plays\",\"last\":\"/api/v1/plays\",\"prev\":null,\"next\":null},\"meta\":{\"current_page\":1,\"from\":1,\"last_page\":1,\"links\":[],\"path\":\"/api/v1/plays\",\"per_page\":15,\"to\":1,\"total\":1}}"));
        server.start();
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void sendsBearerTokenAndParsesProfilesAndPlays() throws Exception {
        MaiteaClient client = new MaiteaClient("secret", baseUrl);

        List<MaiteaModels.Profile> profiles = client.getProfiles();
        List<MaiteaModels.Play> plays = client.getPlays().currentPage();

        assertEquals("Bearer secret", authorization.get());
        assertEquals(7, profiles.get(0).id);
        assertEquals("ＭＡＩ", profiles.get(0).name);
        assertEquals("Track", plays.get(0).song.name.en);
        assertEquals("master", plays.get(0).difficultyLevel.value);
    }

    private final class JsonHandler implements HttpHandler {
        private final String json;

        private JsonHandler(String json) {
            this.json = json;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            OutputStream outputStream = exchange.getResponseBody();
            try {
                outputStream.write(body);
            } finally {
                outputStream.close();
            }
        }
    }
}
