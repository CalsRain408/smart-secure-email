package com.agent.smartsecureemail.mail;

import static com.agent.smartsecureemail.support.ApiTestSupport.address;
import static com.agent.smartsecureemail.support.ApiTestSupport.uniqueUsername;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agent.smartsecureemail.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MailFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private ApiTestSupport api;
    private String alice;
    private String bob;
    private MockHttpSession aliceSession;
    private MockHttpSession bobSession;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiTestSupport(mvc, objectMapper);
        alice = uniqueUsername("alice");
        bob = uniqueUsername("bob");
        aliceSession = api.registerAndLogin(alice);
        bobSession = api.registerAndLogin(bob);
    }

    // ------------------------------------------------------------ helpers

    private static Map<String, Object> compose(List<String> to, List<String> cc, List<String> bcc,
                                               String subject, String body) {
        Map<String, Object> m = new HashMap<>();
        m.put("to", to);
        m.put("cc", cc);
        m.put("bcc", bcc);
        m.put("subject", subject);
        m.put("body", body);
        return m;
    }

    private static Map<String, Object> compose(List<String> to, String subject) {
        return compose(to, List.of(), List.of(), subject, "Hello from the test suite.");
    }

    private JsonNode createDraft(MockHttpSession session, Map<String, Object> body) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/drafts").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(api.json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return api.body(r).get("data");
    }

    private JsonNode sendNew(MockHttpSession session, Map<String, Object> body) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/mails").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(api.json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return api.body(r).get("data");
    }

    private JsonNode list(MockHttpSession session, String folder) throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/mails").param("folder", folder).session(session))
                .andExpect(status().isOk())
                .andReturn();
        return api.body(r).get("data");
    }

    private JsonNode findBySubject(MockHttpSession session, String folder, String subject) throws Exception {
        for (JsonNode item : list(session, folder).get("items")) {
            if (subject.equals(item.get("subject").asText())) {
                return item;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ drafts

    @Test
    void draftIsPersistedUpdatedAndDeleted() throws Exception {
        JsonNode draft = createDraft(aliceSession, compose(List.of(address(bob)), "Draft v1"));
        long id = draft.get("id").asLong();
        assertThat(draft.get("folder").asText()).isEqualTo("DRAFTS");
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");

        mvc.perform(put("/api/v1/drafts/{id}", id).session(aliceSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(compose(List.of(), List.of(address(bob)), List.of(), "Draft v2", "edited"))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/mails/{id}", id).session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject").value("Draft v2"))
                .andExpect(jsonPath("$.data.body").value("edited"))
                .andExpect(jsonPath("$.data.to").isEmpty())
                .andExpect(jsonPath("$.data.cc[0]").value(address(bob)));
        assertThat(findBySubject(aliceSession, "DRAFTS", "Draft v2")).isNotNull();

        mvc.perform(delete("/api/v1/drafts/{id}", id).session(aliceSession).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/mails/{id}", id).session(aliceSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void draftMayBeIncompleteButCannotBeSentWithoutRecipients() throws Exception {
        long id = createDraft(aliceSession, compose(List.of(), List.of(), List.of(), null, null)).get("id").asLong();

        mvc.perform(post("/api/v1/drafts/{id}/send", id).session(aliceSession).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MAIL_RECIPIENT_INVALID"));
    }

    // ------------------------------------------------------------ local delivery

    @Test
    void sendingDraftDeliversToRecipientInboxAndMovesToSent() throws Exception {
        long draftId = createDraft(aliceSession, compose(List.of(address(bob)), "Quarterly plan")).get("id").asLong();

        mvc.perform(post("/api/v1/drafts/{id}/send", draftId).session(aliceSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.folder").value("SENT"))
                .andExpect(jsonPath("$.data.status").value("SENT"))
                .andExpect(jsonPath("$.data.sentAt").isNotEmpty())
                .andExpect(jsonPath("$.data.delivery[0].address").value(address(bob)))
                .andExpect(jsonPath("$.data.delivery[0].status").value("DELIVERED"));

        assertThat(findBySubject(aliceSession, "DRAFTS", "Quarterly plan")).isNull();
        assertThat(findBySubject(aliceSession, "SENT", "Quarterly plan")).isNotNull();

        JsonNode inboxItem = findBySubject(bobSession, "INBOX", "Quarterly plan");
        assertThat(inboxItem).isNotNull();
        assertThat(inboxItem.get("read").asBoolean()).isFalse();
        assertThat(inboxItem.get("from").get("address").asText()).isEqualTo(address(alice));
        assertThat(inboxItem.get("from").get("displayName").asText()).isEqualTo("User " + alice);
    }

    @Test
    void groupSendDeliversToAllAndHidesBccFromRecipients() throws Exception {
        String carol = uniqueUsername("carol");
        String dave = uniqueUsername("dave");
        MockHttpSession carolSession = api.registerAndLogin(carol);
        MockHttpSession daveSession = api.registerAndLogin(dave);

        JsonNode sent = sendNew(aliceSession, compose(
                List.of(address(bob)), List.of(address(carol)), List.of(address(dave)), "Team sync", "Agenda"));

        // Author sees everything, including BCC and delivery state.
        assertThat(sent.get("bcc").get(0).asText()).isEqualTo(address(dave));
        assertThat(sent.get("delivery")).hasSize(3);

        for (MockHttpSession s : List.of(bobSession, carolSession, daveSession)) {
            JsonNode item = findBySubject(s, "INBOX", "Team sync");
            assertThat(item).isNotNull();
            mvc.perform(get("/api/v1/mails/{id}", item.get("id").asLong()).session(s))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.to[0]").value(address(bob)))
                    .andExpect(jsonPath("$.data.cc[0]").value(address(carol)))
                    .andExpect(jsonPath("$.data.bcc").doesNotExist())
                    .andExpect(jsonPath("$.data.delivery").doesNotExist());
        }
    }

    @Test
    void unknownLocalRecipientRejectsWholeSendAndPersistsNothing() throws Exception {
        int sentBefore = list(aliceSession, "SENT").get("totalElements").asInt();
        int draftsBefore = list(aliceSession, "DRAFTS").get("totalElements").asInt();
        String ghost = address(uniqueUsername("ghost"));

        mvc.perform(post("/api/v1/mails").session(aliceSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(compose(List.of(address(bob), ghost), "Should fail"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MAIL_RECIPIENT_INVALID"))
                .andExpect(jsonPath("$.error.details[0].field").value(ghost))
                .andExpect(jsonPath("$.error.details[0].reason").value("UNKNOWN_RECIPIENT"));

        assertThat(list(aliceSession, "SENT").get("totalElements").asInt()).isEqualTo(sentBefore);
        assertThat(list(aliceSession, "DRAFTS").get("totalElements").asInt()).isEqualTo(draftsBefore);
        assertThat(findBySubject(bobSession, "INBOX", "Should fail")).isNull();
    }

    @Test
    void otherDomainIsRejectedUntilFederationExists() throws Exception {
        mvc.perform(post("/api/v1/mails").session(aliceSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(compose(List.of("someone@domain-b.test"), "Cross domain"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].reason").value("DOMAIN_NOT_SUPPORTED"));
    }

    @Test
    void sentMailCannotBeEditedOrResent() throws Exception {
        long id = sendNew(aliceSession, compose(List.of(address(bob)), "Final")).get("id").asLong();

        mvc.perform(put("/api/v1/drafts/{id}", id).session(aliceSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(api.json(compose(List.of(), "tamper"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MAIL_INVALID_STATE"));
        mvc.perform(post("/api/v1/drafts/{id}/send", id).session(aliceSession).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void recipientCanMarkReadAndStar() throws Exception {
        sendNew(aliceSession, compose(List.of(address(bob)), "Flag me"));
        long inboxId = findBySubject(bobSession, "INBOX", "Flag me").get("id").asLong();

        mvc.perform(patch("/api/v1/mails/{id}", inboxId).session(bobSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"read\":true,\"starred\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.starred").value(true));

        mvc.perform(patch("/api/v1/mails/{id}", inboxId).session(bobSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void htmlInBodyIsStoredAsInertText() throws Exception {
        String payload = "<script>fetch('/api/v1/auth/logout',{method:'POST'})</script><img src=x onerror=alert(1)>";
        sendNew(aliceSession, compose(List.of(address(bob)), List.of(), List.of(), "XSS probe", payload));
        long inboxId = findBySubject(bobSession, "INBOX", "XSS probe").get("id").asLong();

        // Returned verbatim as a JSON string (never as HTML); rendering must escape it (client rule).
        mvc.perform(get("/api/v1/mails/{id}", inboxId).session(bobSession))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.body").value(payload));
    }

    // ------------------------------------------------------------ authorization

    @Test
    void usersCannotAccessOrModifyOthersMail() throws Exception {
        long aliceDraft = createDraft(aliceSession, compose(List.of(address(bob)), "Private draft")).get("id").asLong();
        long aliceSent = sendNew(aliceSession, compose(List.of(address(bob)), "Private sent")).get("id").asLong();

        // Bob knows (or guesses) Alice's ids; every access looks like "not found".
        mvc.perform(get("/api/v1/mails/{id}", aliceDraft).session(bobSession))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/mails/{id}", aliceSent).session(bobSession))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/drafts/{id}", aliceDraft).session(bobSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(api.json(compose(List.of(), "hijack"))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/drafts/{id}/send", aliceDraft).session(bobSession).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/drafts/{id}", aliceDraft).session(bobSession).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/mails/{id}", aliceSent).session(bobSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"starred\":true}"))
                .andExpect(status().isNotFound());

        // Alice's draft is untouched.
        mvc.perform(get("/api/v1/mails/{id}", aliceDraft).session(aliceSession))
                .andExpect(jsonPath("$.data.subject").value("Private draft"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void mailEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/mails").param("folder", "INBOX"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/mails").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listParametersAreValidated() throws Exception {
        mvc.perform(get("/api/v1/mails").param("folder", "INBOX").param("size", "101").session(aliceSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/mails").param("folder", "ALL_USERS").session(aliceSession))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/mails").session(aliceSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(api.json(compose(List.of("not-an-address"), "x"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("to[0]"));
    }

    // ------------------------------------------------------------ concurrency

    @Test
    void concurrentSendOfSameDraftDeliversExactlyOnce() throws Exception {
        long draftId = createDraft(aliceSession, compose(List.of(address(bob)), "Send once")).get("id").asLong();
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(post("/api/v1/drafts/{id}/send", draftId)
                                    .session(aliceSession).with(csrf()))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }

            assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
            assertThat(statuses).filteredOn(s -> s != 200).allMatch(s -> s == 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(list(bobSession, "INBOX").get("items").findValuesAsText("subject"))
                .filteredOn("Send once"::equals).hasSize(1);
    }
}
