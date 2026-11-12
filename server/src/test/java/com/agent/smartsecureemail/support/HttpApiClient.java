package com.agent.smartsecureemail.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Minimal browser-like client for real HTTP tests: keeps the session cookie and the
 * session-bound CSRF token, exactly as the SPA will.
 */
public final class HttpApiClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newHttpClient();
    private final String baseUrl;
    private String sessionCookie; // "NAME=value"; the name differs per domain (SSM_SESSION_A/B)
    private String csrfToken;

    public HttpApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void refreshCsrf() throws Exception {
        csrfToken = get("/api/v1/auth/csrf").json().at("/data/token").asText();
    }

    public Response registerAndLogin(String username, String password) throws Exception {
        refreshCsrf();
        Response reg = post("/api/v1/auth/register", Map.of("username", username, "displayName", "User " + username,
                "password", password, "confirmPassword", password));
        if (reg.status() != 201) {
            return reg;
        }
        return login(username, password);
    }

    public Response login(String username, String password) throws Exception {
        if (csrfToken == null) {
            refreshCsrf();
        }
        Response login = post("/api/v1/auth/login", Map.of("username", username, "password", password));
        if (login.status() == 200) {
            refreshCsrf(); // token rotates on login
        }
        return login;
    }

    public Response get(String path) throws Exception {
        return send(builder(path).GET());
    }

    public Response post(String path, Object body) throws Exception {
        HttpRequest.Builder b = builder(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (csrfToken != null) {
            b.header("X-CSRF-TOKEN", csrfToken);
        }
        return send(b);
    }

    private HttpRequest.Builder builder(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (sessionCookie != null) {
            b.header("Cookie", sessionCookie);
        }
        return b;
    }

    private Response send(HttpRequest.Builder builder) throws Exception {
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        response.headers().allValues("Set-Cookie").stream()
                .filter(h -> h.startsWith("SSM_SESSION") && !h.substring(0, h.indexOf(';')).endsWith("="))
                .reduce((a, b) -> b)
                .ifPresent(h -> sessionCookie = h.substring(0, h.indexOf(';')));
        return new Response(response.statusCode(), response.body());
    }

    public record Response(int status, String body) {

        public JsonNode json() throws Exception {
            return JSON.readTree(body);
        }
    }
}
