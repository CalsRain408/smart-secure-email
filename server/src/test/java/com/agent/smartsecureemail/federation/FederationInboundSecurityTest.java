package com.agent.smartsecureemail.federation;

import static com.agent.smartsecureemail.support.ApiTestSupport.address;
import static com.agent.smartsecureemail.support.ApiTestSupport.uniqueUsername;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agent.smartsecureemail.audit.AuditEventRepository;
import com.agent.smartsecureemail.audit.AuditEventType;
import com.agent.smartsecureemail.federation.protocol.FederationHeaders;
import com.agent.smartsecureemail.federation.protocol.FederationSigner;
import com.agent.smartsecureemail.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Inbound federation endpoint as seen by a (possibly malicious) peer. The local domain is
 * {@code domain-test.test}; {@code domain-b.test} is the only configured peer.
 */
@SpringBootTest(properties = {
    "ssm.federation.peers[0].domain=domain-b.test",
    "ssm.federation.peers[0].base-url=http://localhost:1",
    "ssm.federation.peers[0].key-id=test-key",
    "ssm.federation.peers[0].shared-secret=" + FederationInboundSecurityTest.SECRET,
    "ssm.federation.max-envelope-bytes=8192",
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FederationInboundSecurityTest {

    static final String SECRET = "inbound-test-shared-secret-0123456789";
    private static final String PEER = "domain-b.test";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AuditEventRepository auditEvents;

    private ApiTestSupport api;
    private String bob;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport(mvc, objectMapper);
        bob = uniqueUsername("bob");
        api.register(bob);
    }

    // ------------------------------------------------------------ helpers

    private static Map<String, Object> envelope(String messageId, String from, List<Map<String, String>> recipients,
                                                List<String> visibleTo, String subject) {
        Map<String, Object> e = new HashMap<>();
        e.put("protocolVersion", 1);
        e.put("messageId", messageId);
        e.put("from", Map.of("address", from, "displayName", "Alice B"));
        e.put("recipients", recipients);
        e.put("visibleTo", visibleTo);
        e.put("visibleCc", List.of());
        e.put("subject", subject);
        e.put("bodyText", "Hello across domains");
        e.put("sentAt", "2026-11-12T08:00:00Z");
        return e;
    }

    private static Map<String, String> to(String address) {
        return Map.of("address", address, "type", "TO");
    }

    private static String newMessageId(String domain) {
        return UUID.randomUUID() + "@" + domain;
    }

    /** Builds a request signed exactly like a peer would, with optional tampering knobs. */
    private MockHttpServletRequestBuilder signed(Object body, String origin, String keyId, String secret,
                                                 long timestamp, String nonce) throws Exception {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        String ts = Long.toString(timestamp);
        String sha = FederationSigner.contentSha256(bytes);
        String sig = FederationSigner.sign(secret.getBytes(StandardCharsets.UTF_8), FederationSigner.canonical(
                "POST", FederationHeaders.MESSAGES_PATH, origin, keyId, ts, nonce, sha));
        return post(FederationHeaders.MESSAGES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bytes)
                .header(FederationHeaders.ORIGIN, origin)
                .header(FederationHeaders.KEY_ID, keyId)
                .header(FederationHeaders.TIMESTAMP, ts)
                .header(FederationHeaders.NONCE, nonce)
                .header(FederationHeaders.CONTENT_SHA256, sha)
                .header(FederationHeaders.SIGNATURE, sig);
    }

    private ResultActions deliver(Object body) throws Exception {
        return mvc.perform(signed(body, PEER, "test-key", SECRET, now(), UUID.randomUUID().toString()));
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }

    private int inboxCount(MockHttpSession session, String subject) throws Exception {
        JsonNode items = api.body(mvc.perform(get("/api/v1/mails").param("folder", "INBOX").session(session))
                .andReturn()).at("/data/items");
        int n = 0;
        for (JsonNode item : items) {
            if (subject.equals(item.get("subject").asText())) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------ accepted deliveries

    @Test
    void validSignedEnvelopeIsDeliveredToLocalRecipient() throws Exception {
        String subject = "Hello " + bob;
        deliver(envelope(newMessageId(PEER), "alice@domain-b.test", List.of(to(address(bob))),
                List.of(address(bob)), subject))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].address").value(address(bob)))
                .andExpect(jsonPath("$.results[0].status").value("ACCEPTED"));

        MockHttpSession session = api.login(bob);
        assertThat(inboxCount(session, subject)).isEqualTo(1);
        JsonNode item = api.body(mvc.perform(get("/api/v1/mails").param("folder", "INBOX").session(session))
                .andReturn()).at("/data/items/0");
        assertThat(item.at("/from/address").asText()).isEqualTo("alice@domain-b.test");
        assertThat(item.at("/from/displayName").asText()).isEqualTo("Alice B");
    }

    @Test
    void resendWithNewNonceIsIdempotent() throws Exception {
        String subject = "Once " + bob;
        Map<String, Object> env = envelope(newMessageId(PEER), "alice@domain-b.test",
                List.of(to(address(bob))), List.of(address(bob)), subject);

        deliver(env).andExpect(jsonPath("$.results[0].status").value("ACCEPTED"));
        deliver(env).andExpect(status().isOk()).andExpect(jsonPath("$.results[0].status").value("ACCEPTED"));

        assertThat(inboxCount(api.login(bob), subject)).isEqualTo(1);
    }

    @Test
    void resultsArePerRecipient() throws Exception {
        String ghost = address(uniqueUsername("ghost"));
        deliver(envelope(newMessageId(PEER), "alice@domain-b.test",
                List.of(to(address(bob)), to(ghost), to("carol@domain-c.test")),
                List.of(address(bob), ghost, "carol@domain-c.test"), "Mixed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$.results[1].status").value("REJECTED"))
                .andExpect(jsonPath("$.results[1].reason").value("RECIPIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.results[2].status").value("REJECTED"))
                .andExpect(jsonPath("$.results[2].reason").value("RECIPIENT_DOMAIN_MISMATCH"));
    }

    @Test
    void bccRecipientIsDeliveredButNotDisclosed() throws Exception {
        String dave = uniqueUsername("dave");
        api.register(dave);
        String subject = "Bcc " + dave;
        deliver(envelope(newMessageId(PEER), "alice@domain-b.test",
                List.of(to(address(bob)), Map.of("address", address(dave), "type", "BCC")),
                List.of(address(bob)), subject))
                .andExpect(jsonPath("$.results[1].status").value("ACCEPTED"));

        for (String user : List.of(bob, dave)) {
            MockHttpSession session = api.login(user);
            long id = api.body(mvc.perform(get("/api/v1/mails").param("folder", "INBOX").session(session))
                    .andReturn()).at("/data/items/0/id").asLong();
            mvc.perform(get("/api/v1/mails/{id}", id).session(session))
                    .andExpect(jsonPath("$.data.subject").value(subject))
                    .andExpect(jsonPath("$.data.to").value(org.hamcrest.Matchers.contains(address(bob))))
                    .andExpect(jsonPath("$.data.bcc").doesNotExist())
                    .andExpect(jsonPath("$.data.delivery").doesNotExist());
        }
    }

    // ------------------------------------------------------------ authentication failures

    @Test
    void tamperedBodyIsRejected() throws Exception {
        Map<String, Object> original = envelope(newMessageId(PEER), "alice@domain-b.test",
                List.of(to(address(bob))), List.of(address(bob)), "Original");
        Map<String, Object> tampered = new HashMap<>(original);
        tampered.put("subject", "Tampered");

        MockHttpServletRequestBuilder request = signed(original, PEER, "test-key", SECRET, now(), UUID.randomUUID().toString())
                .content(objectMapper.writeValueAsBytes(tampered));
        mvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("PEER_SIGNATURE_INVALID"));
    }

    @Test
    void wrongSecretIsRejected() throws Exception {
        mvc.perform(signed(envelope(newMessageId(PEER), "alice@domain-b.test", List.of(to(address(bob))),
                        List.of(), "x"), PEER, "test-key", "attacker-guessed-secret-0123456789xx", now(),
                        UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("PEER_SIGNATURE_INVALID"));
    }

    @Test
    void unknownPeerOrKeyIdIsRejectedAndAudited() throws Exception {
        mvc.perform(signed(envelope(newMessageId("domain-c.test"), "eve@domain-c.test", List.of(to(address(bob))),
                        List.of(), "x"), "domain-c.test", "test-key", SECRET, now(), UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("PEER_UNKNOWN"));
        mvc.perform(signed(envelope(newMessageId(PEER), "alice@domain-b.test", List.of(to(address(bob))),
                        List.of(), "x"), PEER, "old-key", SECRET, now(), UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("PEER_UNKNOWN"));

        assertThat(auditEvents.findByEventTypeAndActorIdentifierOrderByIdAsc(
                AuditEventType.FEDERATION_AUTH_FAILED, "domain-c.test")).isNotEmpty();
    }

    @Test
    void staleOrFutureTimestampIsRejected() throws Exception {
        Map<String, Object> env = envelope(newMessageId(PEER), "alice@domain-b.test", List.of(to(address(bob))),
                List.of(), "x");
        mvc.perform(signed(env, PEER, "test-key", SECRET, now() - 3600, UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("PEER_TIMESTAMP_SKEW"));
        mvc.perform(signed(env, PEER, "test-key", SECRET, now() + 3600, UUID.randomUUID().toString()))
                .andExpect(jsonPath("$.error.code").value("PEER_TIMESTAMP_SKEW"));
    }

    @Test
    void replayedRequestIsRejected() throws Exception {
        Map<String, Object> env = envelope(newMessageId(PEER), "alice@domain-b.test", List.of(to(address(bob))),
                List.of(address(bob)), "Replay");
        long ts = now();
        String nonce = UUID.randomUUID().toString();

        mvc.perform(signed(env, PEER, "test-key", SECRET, ts, nonce)).andExpect(status().isOk());
        // Byte-for-byte identical capture replayed by an attacker.
        mvc.perform(signed(env, PEER, "test-key", SECRET, ts, nonce))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PEER_REPLAY"));
    }

    @Test
    void missingSignatureHeadersAreRejected() throws Exception {
        mvc.perform(post(FederationHeaders.MESSAGES_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oversizedBodyIsRejected() throws Exception {
        Map<String, Object> env = envelope(newMessageId(PEER), "alice@domain-b.test", List.of(to(address(bob))),
                List.of(), "Big");
        env.put("bodyText", "x".repeat(10_000)); // limit in this test context: 8 KiB
        deliver(env).andExpect(status().isPayloadTooLarge());
    }

    // ------------------------------------------------------------ authenticated peer, bad content

    @Test
    void peerCannotSpoofSenderOfAnotherDomain() throws Exception {
        deliver(envelope(newMessageId(PEER), "ceo@domain-c.test", List.of(to(address(bob))), List.of(), "Spoof"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ORIGIN_MISMATCH"));
        deliver(envelope(newMessageId("domain-c.test"), "alice@domain-b.test", List.of(to(address(bob))),
                List.of(), "Foreign id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ORIGIN_MISMATCH"));
    }

    @Test
    void invalidEnvelopeIsRejected() throws Exception {
        Map<String, Object> env = envelope(newMessageId(PEER), "alice@domain-b.test", List.of(), List.of(), "x");
        deliver(env).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        Map<String, Object> unknownField = envelope(newMessageId(PEER), "alice@domain-b.test",
                List.of(to(address(bob))), List.of(), "x");
        unknownField.put("senderUserId", 1);
        deliver(unknownField).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ chain separation

    @Test
    void userSessionCannotCallFederationEndpoint() throws Exception {
        MockHttpSession session = api.login(bob);
        mvc.perform(post(FederationHeaders.MESSAGES_PATH).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(envelope(newMessageId(PEER), "alice@domain-b.test",
                                List.of(to(address(bob))), List.of(), "x"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void peerSignatureHeadersDoNotGrantUserApiAccess() throws Exception {
        // Federation headers mean nothing to the browser API chain: it requires a user session.
        mvc.perform(get("/api/v1/mails").param("folder", "INBOX")
                        .header(FederationHeaders.ORIGIN, PEER)
                        .header(FederationHeaders.KEY_ID, "test-key")
                        .header(FederationHeaders.SIGNATURE, "anything"))
                .andExpect(status().isUnauthorized());
    }
}
