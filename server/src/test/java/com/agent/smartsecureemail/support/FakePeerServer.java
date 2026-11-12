package com.agent.smartsecureemail.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;

/**
 * Scriptable stand-in for a peer mail server (JDK built-in HTTP server, no extra dependency).
 * Records every request and answers with queued replies, falling back to a default.
 */
public final class FakePeerServer implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    private final List<Recorded> requests = Collections.synchronizedList(new ArrayList<>());
    private final Deque<Function<Recorded, Reply>> script = new ConcurrentLinkedDeque<>();
    private volatile Function<Recorded, Reply> fallback = FakePeerServer::acceptAll;

    public FakePeerServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            Recorded recorded = new Recorded(exchange.getRequestURI().getPath(),
                    Map.copyOf(exchange.getRequestHeaders()), body);
            requests.add(recorded);
            Function<Recorded, Reply> responder = script.poll();
            Reply reply = (responder != null ? responder : fallback).apply(recorded);
            byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void reset() {
        requests.clear();
        script.clear();
        fallback = FakePeerServer::acceptAll;
    }

    public void enqueue(Function<Recorded, Reply> responder) {
        script.add(responder);
    }

    public void setFallback(Function<Recorded, Reply> responder) {
        fallback = responder;
    }

    public List<Recorded> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ------------------------------------------------------------ canned replies

    public static Reply status(int status) {
        return new Reply(status, "{\"success\":false,\"error\":{\"code\":\"FAKE\",\"message\":\"fake\"}}");
    }

    /** Accepts every recipient in the envelope. */
    public static Reply acceptAll(Recorded request) {
        return receipt(request, address -> null);
    }

    /** Builds a receipt; {@code rejectReason} returns a reason to reject, or null to accept. */
    public static Reply receipt(Recorded request, Function<String, String> rejectReason) {
        try {
            JsonNode envelope = JSON.readTree(request.body());
            ObjectNode receipt = JSON.createObjectNode();
            receipt.put("protocolVersion", 1);
            receipt.put("messageId", envelope.get("messageId").asText());
            ArrayNode results = receipt.putArray("results");
            for (JsonNode recipient : envelope.get("recipients")) {
                String address = recipient.get("address").asText();
                String reason = rejectReason.apply(address);
                ObjectNode r = results.addObject().put("address", address)
                        .put("status", reason == null ? "ACCEPTED" : "REJECTED");
                if (reason != null) {
                    r.put("reason", reason);
                }
            }
            return new Reply(200, JSON.writeValueAsString(receipt));
        } catch (IOException e) {
            return status(500);
        }
    }

    public record Recorded(String path, Map<String, List<String>> headers, byte[] body) {

        public String header(String name) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().get(0))
                    .findFirst().orElse(null);
        }

        public JsonNode json() throws IOException {
            return JSON.readTree(body);
        }
    }

    public record Reply(int status, String body) {
    }
}
