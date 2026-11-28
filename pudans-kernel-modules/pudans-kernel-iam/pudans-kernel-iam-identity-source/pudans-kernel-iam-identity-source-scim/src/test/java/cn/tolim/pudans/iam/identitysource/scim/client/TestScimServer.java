package cn.tolim.pudans.iam.identitysource.scim.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;


public final class TestScimServer implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, BiFunction<String, String, StubResponse>> handlers =
            new ConcurrentHashMap<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();

    private TestScimServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public static TestScimServer start() throws IOException {
        return new TestScimServer();
    }

    public TestScimServer on(String path, String body) {
        return on(path, StubResponse.ok(body));
    }

    public TestScimServer on(String path, StubResponse response) {
        handlers.put(path, (requestPath, query) -> response);
        return this;
    }

    public TestScimServer on(String path, BiFunction<String, String, StubResponse> handler) {
        handlers.put(path, handler);
        return this;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/scim/v2";
    }

    public String lastAuthorization() {
        return lastAuthorization.get();
    }

    public String lastContentType() {
        return lastContentType.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        BiFunction<String, String, StubResponse> handler = handlers.get(path);
        StubResponse response = handler == null
                ? new StubResponse(404, error("notFound", "resource not found"))
                : handler.apply(path, query);
        if (response.body() == null || response.body().isEmpty()) {
            exchange.sendResponseHeaders(response.status(), -1);
        } else {
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/scim+json");
            exchange.sendResponseHeaders(response.status(), bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
        exchange.close();
    }

    private static String error(String scimType, String detail) {
        return """
                {"schemas":["urn:ietf:params:scim:api:messages:2.0:Error"],
                 "status":"404","scimType":"%s","detail":"%s"}
                """.formatted(scimType, detail);
    }

    public record StubResponse(int status, String body) {

        public static StubResponse ok(String body) {
            return new StubResponse(200, body);
        }
    }
}