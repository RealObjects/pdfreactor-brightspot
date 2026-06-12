package com.realobjects.brightspot.pdfreactor.render;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.realobjects.brightspot.pdfreactor.PdfReactorException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the HTTP-fetch half of {@link PermalinkHtmlSource} against a local
 * server. (Permalink resolution itself needs a Dari database and is covered
 * by manual verification in the local CMS stack.)
 */
class PermalinkHtmlSourceFetchTest {

    private HttpServer server;
    private String baseUrl;
    private final PermalinkHtmlSource source = new PermalinkHtmlSource(5_000);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    @Test
    void fetchesHtmlAsUtf8() {
        respond("/article", 200, "<html><body>Grüße</body></html>");

        assertThat(source.fetch(baseUrl + "/article"))
                .isEqualTo("<html><body>Grüße</body></html>");
    }

    @Test
    void followsRedirects() {
        server.createContext("/old", exchange -> {
            exchange.getResponseHeaders().set("Location", baseUrl + "/new");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        respond("/new", 200, "<html>moved</html>");

        assertThat(source.fetch(baseUrl + "/old")).isEqualTo("<html>moved</html>");
    }

    @Test
    void non200StatusFails() {
        respond("/missing", 404, "not found");

        assertThatThrownBy(() -> source.fetch(baseUrl + "/missing"))
                .isInstanceOf(PdfReactorException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void unreachableHostFails() {
        assertThatThrownBy(() -> source.fetch("http://localhost:1/nope"))
                .isInstanceOf(PdfReactorException.class)
                .hasMessageContaining("Failed to fetch");
    }

    @Test
    void refusesRedirectToDisallowedHost() {
        server.createContext("/pivot", exchange -> {
            // 127.0.0.1 is a different host string than the start "localhost".
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:1/internal");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        assertThatThrownBy(() -> source.fetch(baseUrl + "/pivot"))
                .isInstanceOf(PdfReactorException.class)
                .hasMessageContaining("disallowed host");
    }

    @Test
    void refusesNonHttpRedirectScheme() {
        server.createContext("/scheme", exchange -> {
            exchange.getResponseHeaders().set("Location", "ftp://localhost/secret");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        assertThatThrownBy(() -> source.fetch(baseUrl + "/scheme"))
                .isInstanceOf(PdfReactorException.class)
                .hasMessageContaining("non-HTTP(S)");
    }

    @Test
    void rejectsNonHtmlContentType() {
        server.createContext("/json", exchange -> {
            byte[] bytes = "{\"x\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });

        assertThatThrownBy(() -> source.fetch(baseUrl + "/json"))
                .isInstanceOf(PdfReactorException.class)
                .hasMessageContaining("Expected HTML");
    }

    @Test
    void decodesByResponseCharset() {
        server.createContext("/latin1", exchange -> {
            byte[] bytes = "<html>Grüße</html>".getBytes(StandardCharsets.ISO_8859_1);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=ISO-8859-1");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });

        assertThat(source.fetch(baseUrl + "/latin1")).isEqualTo("<html>Grüße</html>");
    }
}
