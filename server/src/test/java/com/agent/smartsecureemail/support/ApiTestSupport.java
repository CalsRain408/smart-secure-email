package com.agent.smartsecureemail.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Drives the real HTTP API (register → login → session) instead of injecting fake
 * principals, so tests exercise the same authentication path as the client.
 */
public final class ApiTestSupport {

    public static final String PASSWORD = "Str0ngPassw0rd";
    public static final String DOMAIN = "domain-test.test";

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final MockMvc mvc;
    private final ObjectMapper objectMapper;

    public ApiTestSupport(MockMvc mvc, ObjectMapper objectMapper) {
        this.mvc = mvc;
        this.objectMapper = objectMapper;
    }

    /** Unique, policy-compliant username (contexts and their DB are shared across test classes). */
    public static String uniqueUsername(String prefix) {
        return prefix + "-" + Long.toString(System.nanoTime() % 1_000_000, 36) + SEQ.incrementAndGet();
    }

    public static String address(String username) {
        return username + "@" + DOMAIN;
    }

    public void register(String username) throws Exception {
        mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "displayName", "User " + username,
                                "password", PASSWORD,
                                "confirmPassword", PASSWORD))))
                .andExpect(status().isCreated());
    }

    public MockHttpSession login(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    public MockHttpSession registerAndLogin(String username) throws Exception {
        register(username);
        return login(username);
    }

    public String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    public JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
