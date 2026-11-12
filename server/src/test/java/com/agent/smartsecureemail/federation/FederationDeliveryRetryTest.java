package com.agent.smartsecureemail.federation;

import static com.agent.smartsecureemail.support.ApiTestSupport.address;
import static com.agent.smartsecureemail.support.ApiTestSupport.uniqueUsername;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agent.smartsecureemail.federation.protocol.FederationHeaders;
import com.agent.smartsecureemail.federation.protocol.FederationSigner;
import com.agent.smartsecureemail.support.ApiTestSupport;
import com.agent.smartsecureemail.support.FakePeerServer;
import com.agent.smartsecureemail.support.FakePeerServer.Recorded;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Outbound delivery against a scripted fake peer: signing, acknowledgement handling,
 * retry with backoff, permanent failures and giving up.
 */
@SpringBootTest(properties = {
    "ssm.federation.dispatch-interval-ms=100",
    "ssm.federation.retry-delays=150ms,150ms",
    "ssm.federation.request-timeout=2s",
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FederationDeliveryRetryTest {

    static final String SECRET = "retry-test-shared-secret-0123456789ab";
    private static final FakePeerServer PEER = startPeer();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private ApiTestSupport api;
    private MockHttpSession alice;
    private String aliceName;

    private static FakePeerServer startPeer() {
        try {
            return new FakePeerServer();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * All peer fields live in one property source: Spring Boot takes an indexed list from the
     * highest-priority source only, so splitting peers[0] across sources would drop fields.
     */
    @DynamicPropertySource
    static void peer(DynamicPropertyRegistry registry) {
        registry.add("ssm.federation.peers[0].domain", () -> "domain-b.test");
        registry.add("ssm.federation.peers[0].base-url", () -> "http://localhost:" + PEER.port());
        registry.add("ssm.federation.peers[0].key-id", () -> "retry-key");
        registry.add("ssm.federation.peers[0].shared-secret", () -> SECRET);
    }

    @AfterAll
    static void stopPeer() {
        PEER.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        PEER.reset();
        api = new ApiTestSupport(mvc, objectMapper);
        aliceName = uniqueUsername("alice");
        alice = api.registerAndLogin(aliceName);
    }

    // ------------------------------------------------------------ helpers

    private JsonNode send(List<String> to, String subject) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("to", to);
        body.put("subject", subject);
        body.put("body", "federation test");
        return api.body(mvc.perform(post("/api/v1/mails").session(alice).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(api.json(body)))
                .andExpect(status().isCreated())
                .andReturn()).get("data");
    }

    private Map<String, String> deliveryStatuses(long sentItemId) throws Exception {
        JsonNode delivery = api.body(mvc.perform(get("/api/v1/mails/{id}", sentItemId).session(alice))
                .andReturn()).at("/data/delivery");
        Map<String, String> statuses = new HashMap<>();
        delivery.forEach(d -> statuses.put(d.get("address").asText(), d.get("status").asText()));
        return statuses;
    }

    /** Polls until every listed recipient has reached a final state (or times out). */
    private Map<String, String> awaitFinal(long sentItemId, Set<String> recipients) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (true) {
            Map<String, String> statuses = deliveryStatuses(sentItemId);
            boolean done = recipients.stream().allMatch(r ->
                    Set.of("DELIVERED", "FAILED").contains(statuses.get(r)));
            if (done || Instant.now().isAfter(deadline)) {
                return statuses;
            }
            Thread.sleep(50);
        }
    }

    private static boolean validlySigned(Recorded r) {
        String canonical = FederationSigner.canonical("POST", r.path(), r.header(FederationHeaders.ORIGIN),
                r.header(FederationHeaders.KEY_ID), r.header(FederationHeaders.TIMESTAMP),
                r.header(FederationHeaders.NONCE), FederationSigner.contentSha256(r.body()));
        return FederationSigner.constantTimeEquals(FederationSigner.contentSha256(r.body()),
                        r.header(FederationHeaders.CONTENT_SHA256))
                && FederationSigner.verify(SECRET.getBytes(StandardCharsets.UTF_8), canonical,
                        r.header(FederationHeaders.SIGNATURE));
    }

    // ------------------------------------------------------------ tests

    @Test
    void remoteRecipientStartsPendingAndIsDeliveredWithSignedEnvelope() throws Exception {
        String carol = uniqueUsername("carol");
        api.register(carol);
        JsonNode sent = send(List.of("bob@domain-b.test", address(carol)), "Mixed delivery");

        // Local recipient is delivered synchronously, remote one is queued.
        Map<String, String> initial = new HashMap<>();
        sent.get("delivery").forEach(d -> initial.put(d.get("address").asText(), d.get("status").asText()));
        assertThat(initial.get(address(carol))).isEqualTo("DELIVERED");
        assertThat(initial.get("bob@domain-b.test")).isIn("PENDING", "DELIVERED");

        Map<String, String> result = awaitFinal(sent.get("id").asLong(), Set.of("bob@domain-b.test"));
        assertThat(result.get("bob@domain-b.test")).isEqualTo("DELIVERED");

        List<Recorded> requests = PEER.requests();
        assertThat(requests).hasSize(1);
        Recorded r = requests.get(0);
        assertThat(r.path()).isEqualTo(FederationHeaders.MESSAGES_PATH);
        assertThat(r.header(FederationHeaders.ORIGIN)).isEqualTo("domain-test.test");
        assertThat(validlySigned(r)).isTrue();
        JsonNode envelope = r.json();
        assertThat(envelope.get("messageId").asText()).endsWith("@domain-test.test");
        assertThat(envelope.at("/from/address").asText()).isEqualTo(address(aliceName));
        // Only the peer's recipients are in the delivery list; visible headers list everyone in TO.
        assertThat(envelope.get("recipients")).hasSize(1);
        assertThat(envelope.get("visibleTo").toString()).contains(address(carol), "bob@domain-b.test");
    }

    @Test
    void transientFailuresAreRetriedWithFreshNoncesThenDelivered() throws Exception {
        PEER.enqueue(r -> FakePeerServer.status(503));
        PEER.enqueue(r -> FakePeerServer.status(500));

        JsonNode sent = send(List.of("bob@domain-b.test"), "Retry me");
        Map<String, String> result = awaitFinal(sent.get("id").asLong(), Set.of("bob@domain-b.test"));

        assertThat(result.get("bob@domain-b.test")).isEqualTo("DELIVERED");
        List<Recorded> requests = PEER.requests();
        assertThat(requests).hasSize(3);
        assertThat(requests).allMatch(FederationDeliveryRetryTest::validlySigned);
        assertThat(requests.stream().map(r -> r.header(FederationHeaders.NONCE)).collect(Collectors.toSet()))
                .as("every attempt uses a new nonce").hasSize(3);
        assertThat(requests.stream().map(r -> {
            try {
                return r.json().get("messageId").asText();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).collect(Collectors.toSet())).as("messageId is stable across retries").hasSize(1);
    }

    @Test
    void permanentRejectionFailsImmediatelyWithoutRetry() throws Exception {
        PEER.setFallback(r -> FakePeerServer.status(401));

        JsonNode sent = send(List.of("bob@domain-b.test"), "Unauthorized");
        Map<String, String> result = awaitFinal(sent.get("id").asLong(), Set.of("bob@domain-b.test"));

        assertThat(result.get("bob@domain-b.test")).isEqualTo("FAILED");
        Thread.sleep(400); // longer than the retry delay: no further attempts may happen
        assertThat(PEER.requests()).hasSize(1);
    }

    @Test
    void givesUpAfterConfiguredAttempts() throws Exception {
        PEER.setFallback(r -> FakePeerServer.status(503));

        JsonNode sent = send(List.of("bob@domain-b.test"), "Peer down");
        Map<String, String> result = awaitFinal(sent.get("id").asLong(), Set.of("bob@domain-b.test"));

        assertThat(result.get("bob@domain-b.test")).isEqualTo("FAILED");
        Thread.sleep(400);
        assertThat(PEER.requests()).as("1 initial attempt + 2 retries").hasSize(3);
    }

    @Test
    void perRecipientRejectionIsReflectedInDeliveryStatus() throws Exception {
        PEER.setFallback(r -> FakePeerServer.receipt(r,
                address -> address.startsWith("ghost") ? "RECIPIENT_NOT_FOUND" : null));

        JsonNode sent = send(List.of("bob@domain-b.test", "ghost@domain-b.test"), "Partial");
        Map<String, String> result = awaitFinal(sent.get("id").asLong(),
                Set.of("bob@domain-b.test", "ghost@domain-b.test"));

        assertThat(result.get("bob@domain-b.test")).isEqualTo("DELIVERED");
        assertThat(result.get("ghost@domain-b.test")).isEqualTo("FAILED");
        assertThat(PEER.requests()).hasSize(1);
    }

    @Test
    void unconfiguredDomainIsStillRejectedSynchronously() throws Exception {
        mvc.perform(post("/api/v1/mails").session(alice).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(Map.of("to", List.of("x@domain-z.test"), "subject", "s", "body", "b"))))
                .andExpect(status().isBadRequest());
        assertThat(PEER.requests()).isEmpty();
    }
}
