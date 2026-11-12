package com.agent.smartsecureemail.auth;

import static com.agent.smartsecureemail.support.ApiTestSupport.PASSWORD;
import static com.agent.smartsecureemail.support.ApiTestSupport.uniqueUsername;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Real HTTP round trips against embedded Tomcat, verifying the browser-facing contract
 * that MockMvc cannot show faithfully: session cookie attributes, session id rotation,
 * session-bound CSRF tokens (rotation on login, death on logout).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessionCookieHttpTest {

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void sessionAndCsrfContract() throws Exception {
        String username = uniqueUsername("web");

        // 1. Anonymous client obtains a CSRF token bound to a fresh (anonymous) session.
        HttpResponse<String> csrf = send(get("/api/v1/auth/csrf", null));
        assertThat(csrf.statusCode()).isEqualTo(200);
        assertThat(json(csrf).at("/data/headerName").asText()).isEqualTo(CSRF_HEADER);
        String anonSession = cookieValue(setCookie(csrf, "SSM_SESSION").orElseThrow());
        String anonToken = json(csrf).at("/data/token").asText();

        // 2. Token accepted only together with the session it belongs to.
        assertThat(send(post("/api/v1/auth/register", registration(username), anonSession, anonToken)).statusCode())
                .isEqualTo(201);
        assertThat(send(post("/api/v1/auth/register", registration(uniqueUsername("web")), anonSession, "forged"))
                .statusCode()).isEqualTo(403);
        assertThat(send(post("/api/v1/auth/register", registration(uniqueUsername("web")), null, anonToken))
                .statusCode()).isEqualTo(403);

        // 3. Login: new session id in a hardened cookie.
        HttpResponse<String> login = send(post("/api/v1/auth/login",
                Map.of("username", username, "password", PASSWORD), anonSession, anonToken));
        assertThat(login.statusCode()).isEqualTo(200);
        String sessionCookie = setCookie(login, "SSM_SESSION").orElseThrow();
        assertThat(sessionCookie)
                .containsIgnoringCase("HttpOnly")
                .containsIgnoringCase("Secure")
                .containsIgnoringCase("SameSite=Strict");
        String session = cookieValue(sessionCookie);
        assertThat(session).isNotEqualTo(anonSession);

        // 4. The pre-login token was rotated away; a fresh one must be fetched.
        assertThat(send(post("/api/v1/auth/logout", Map.of(), session, anonToken)).statusCode()).isEqualTo(403);
        String token = json(send(get("/api/v1/auth/csrf", session))).at("/data/token").asText();

        HttpResponse<String> me = send(get("/api/v1/auth/me", session));
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(json(me).at("/data/username").asText()).isEqualTo(username);

        // 5. Logout invalidates the session server-side; the cookie value is now useless.
        assertThat(send(post("/api/v1/auth/logout", Map.of(), session, token)).statusCode()).isEqualTo(200);
        assertThat(send(get("/api/v1/auth/me", session)).statusCode()).isEqualTo(401);
    }

    // ------------------------------------------------------------ helpers

    private static Map<String, String> registration(String username) {
        return Map.of("username", username, "displayName", "Web User",
                "password", PASSWORD, "confirmPassword", PASSWORD);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpRequest get(String path, String session) {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri(path)).GET();
        if (session != null) {
            b.header("Cookie", "SSM_SESSION=" + session);
        }
        return b.build();
    }

    private HttpRequest post(String path, Object body, String session, String csrfToken) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header(CSRF_HEADER, csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (session != null) {
            b.header("Cookie", "SSM_SESSION=" + session);
        }
        return b.build();
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private static Optional<String> setCookie(HttpResponse<?> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "=") && !h.startsWith(name + "=;"))
                .reduce((first, second) -> second);
    }

    private static String cookieValue(String setCookieHeader) {
        String pair = setCookieHeader.split(";", 2)[0];
        return pair.substring(pair.indexOf('=') + 1);
    }
}
