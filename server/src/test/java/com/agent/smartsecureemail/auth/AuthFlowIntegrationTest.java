package com.agent.smartsecureemail.auth;

import static com.agent.smartsecureemail.support.ApiTestSupport.PASSWORD;
import static com.agent.smartsecureemail.support.ApiTestSupport.uniqueUsername;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agent.smartsecureemail.audit.AuditEventRepository;
import com.agent.smartsecureemail.audit.AuditEventType;
import com.agent.smartsecureemail.support.ApiTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuditEventRepository auditEvents;

    private ApiTestSupport api;

    @BeforeEach
    void setUp() {
        api = new ApiTestSupport(mvc, objectMapper);
    }

    private MockHttpServletRequestBuilder jsonPost(String path, Object body) throws Exception {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(api.json(body));
    }

    private static Map<String, String> registration(String username, String password, String confirm) {
        return Map.of("username", username, "displayName", "Test User",
                "password", password, "confirmPassword", confirm);
    }

    // ------------------------------------------------------------ happy path

    @Test
    void registerLoginMeLogoutFlow() throws Exception {
        String username = uniqueUsername("alice");

        mvc.perform(jsonPost("/api/v1/auth/register", registration(username, PASSWORD, PASSWORD)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.address").value(username + "@domain-test.test"))
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        MockHttpSession session = api.login(username);

        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));

        mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
                .andExpect(status().isOk());

        // The old session is invalidated server-side: reusing it is unauthenticated.
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordIsStoredOnlyAsBcryptHash() throws Exception {
        String username = uniqueUsername("hash");
        api.register(username);

        String stored = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE username = ?", String.class, username);

        assertThat(stored).startsWith("$2").doesNotContain(PASSWORD);
    }

    @Test
    void loginAcceptsFullLocalAddressCaseInsensitively() throws Exception {
        String username = uniqueUsername("carol");
        api.register(username);

        mvc.perform(jsonPost("/api/v1/auth/login",
                        Map.of("username", username.toUpperCase() + "@DOMAIN-TEST.TEST", "password", PASSWORD))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username));
    }

    // ------------------------------------------------------------ registration rules

    @Test
    void registrationRejectsPasswordMismatch() throws Exception {
        mvc.perform(jsonPost("/api/v1/auth/register",
                        registration(uniqueUsername("mm"), PASSWORD, PASSWORD + "x")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH_PASSWORD_MISMATCH"));
    }

    @Test
    void registrationRejectsWeakPassword() throws Exception {
        mvc.perform(jsonPost("/api/v1/auth/register",
                        registration(uniqueUsername("weak"), "onlyletters", "onlyletters")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("password"));
    }

    @Test
    void registrationRejectsDuplicateUsernameCaseInsensitively() throws Exception {
        String username = uniqueUsername("dup");
        api.register(username);

        mvc.perform(jsonPost("/api/v1/auth/register",
                        registration(username.toUpperCase(), PASSWORD, PASSWORD)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_USERNAME_UNAVAILABLE"));
    }

    @Test
    void concurrentRegistrationOfSameUsernameCreatesExactlyOneAccount() throws Exception {
        String username = uniqueUsername("race");
        int threads = 6;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<MvcResult>> futures = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(jsonPost("/api/v1/auth/register",
                            registration(username, PASSWORD, PASSWORD)).with(csrf())).andReturn();
                }));
            }
            start.countDown();
            java.util.List<Integer> statuses = new java.util.ArrayList<>();
            for (java.util.concurrent.Future<MvcResult> f : futures) {
                MvcResult r = f.get();
                statuses.add(r.getResponse().getStatus());
                if (r.getResponse().getStatus() == 409) {
                    assertThat(api.body(r).at("/error/code").asText()).isEqualTo("AUTH_USERNAME_UNAVAILABLE");
                }
            }
            assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
            assertThat(statuses).filteredOn(s -> s != 201).allMatch(s -> s == 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, username))
                .isEqualTo(1);
    }

    @Test
    void registrationRejectsReservedUsernameLikeTakenOne() throws Exception {
        mvc.perform(jsonPost("/api/v1/auth/register", registration("postmaster", PASSWORD, PASSWORD)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_USERNAME_UNAVAILABLE"));
    }

    @Test
    void registrationRejectsMassAssignmentOfRole() throws Exception {
        Map<String, String> body = new java.util.HashMap<>(registration(uniqueUsername("mass"), PASSWORD, PASSWORD));
        body.put("role", "ADMIN");

        mvc.perform(jsonPost("/api/v1/auth/register", body).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    // ------------------------------------------------------------ login failures

    @Test
    void wrongPasswordAndUnknownUserAreIndistinguishable() throws Exception {
        String username = uniqueUsername("eve");
        api.register(username);

        MvcResult wrongPassword = mvc.perform(jsonPost("/api/v1/auth/login",
                        Map.of("username", username, "password", "Wr0ngPassword")).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult unknownUser = mvc.perform(jsonPost("/api/v1/auth/login",
                        Map.of("username", uniqueUsername("ghost"), "password", "Wr0ngPassword")).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(api.body(wrongPassword).at("/error")).isEqualTo(api.body(unknownUser).at("/error"));
        assertThat(api.body(wrongPassword).at("/error/code").asText()).isEqualTo("AUTH_INVALID_CREDENTIALS");
        assertThat(auditEvents.findByEventTypeAndActorIdentifierOrderByIdAsc(
                AuditEventType.AUTH_LOGIN_FAILURE, username)).hasSize(1);
    }

    @Test
    void loginWithForeignDomainAddressIsRejected() throws Exception {
        String username = uniqueUsername("frank");
        api.register(username);

        mvc.perform(jsonPost("/api/v1/auth/login",
                        Map.of("username", username + "@other-domain.test", "password", PASSWORD)).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    // ------------------------------------------------------------ session & CSRF

    @Test
    void loginIssuesNewSessionIdToPreventFixation() throws Exception {
        String username = uniqueUsername("fix");
        api.register(username);
        MockHttpSession preLogin = new MockHttpSession();
        String fixatedId = preLogin.getId();

        MvcResult result = mvc.perform(jsonPost("/api/v1/auth/login",
                        Map.of("username", username, "password", PASSWORD)).session(preLogin).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(fixatedId);
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(jsonPost("/api/v1/auth/login", Map.of("username", "x-user", "password", PASSWORD)))
                .andExpect(status().isForbidden());
    }
}
