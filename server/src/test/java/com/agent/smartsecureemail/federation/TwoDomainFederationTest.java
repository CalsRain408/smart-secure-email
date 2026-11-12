package com.agent.smartsecureemail.federation;

import static org.assertj.core.api.Assertions.assertThat;

import com.agent.smartsecureemail.SmartSecureEmailApplication;
import com.agent.smartsecureemail.support.HttpApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Two independent server instances (separate Spring contexts, ports, in-memory databases and
 * storage roots) exchanging mail over real HTTP using only the federation protocol.
 */
class TwoDomainFederationTest {

    private static final String SECRET = "two-domain-it-shared-secret-0123456789";
    private static final String PASSWORD = "Str0ngPassw0rd";
    private static final AtomicInteger SEQ = new AtomicInteger();

    private static ConfigurableApplicationContext serverA;
    private static ConfigurableApplicationContext serverB;
    private static String urlA;
    private static String urlB;

    @BeforeAll
    static void startBothDomains() throws IOException {
        int portA = freePort();
        int portB = freePort();
        urlA = "http://localhost:" + portA;
        urlB = "http://localhost:" + portB;
        serverA = start("domain-a", portA, "domain-b.test", urlB);
        serverB = start("domain-b", portB, "domain-a.test", urlA);
    }

    @AfterAll
    static void stopBothDomains() {
        if (serverA != null) {
            serverA.close();
        }
        if (serverB != null) {
            serverB.close();
        }
    }

    private static ConfigurableApplicationContext start(String profile, int port, String peerDomain, String peerUrl) {
        // Command-line args have the highest precedence (above the domain profile's port).
        return new SpringApplicationBuilder(SmartSecureEmailApplication.class)
                .profiles("test", profile)
                .run("--server.port=" + port,
                        "--ssm.federation.peers[0].domain=" + peerDomain,
                        "--ssm.federation.peers[0].base-url=" + peerUrl,
                        "--ssm.federation.peers[0].key-id=it-a-b",
                        "--ssm.federation.peers[0].shared-secret=" + SECRET,
                        "--ssm.federation.dispatch-interval-ms=100",
                        "--ssm.federation.retry-delays=200ms,200ms");
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String user(String prefix) {
        return prefix + System.nanoTime() % 100000 + SEQ.incrementAndGet();
    }

    private static HttpApiClient signedIn(String baseUrl, String username) throws Exception {
        HttpApiClient client = new HttpApiClient(baseUrl);
        assertThat(client.registerAndLogin(username, PASSWORD).status()).isEqualTo(200);
        return client;
    }

    private static JsonNode send(HttpApiClient client, Map<String, Object> body) throws Exception {
        HttpApiClient.Response r = client.post("/api/v1/mails", body);
        assertThat(r.status()).as(r.body()).isEqualTo(201);
        return r.json().get("data");
    }

    /** Polls the author's sent copy until the recipient leaves PENDING/RETRYING. */
    private static String awaitDelivery(HttpApiClient author, long sentId, String recipient) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        String status = null;
        while (Instant.now().isBefore(deadline)) {
            for (JsonNode d : author.get("/api/v1/mails/" + sentId).json().at("/data/delivery")) {
                if (d.get("address").asText().equals(recipient)) {
                    status = d.get("status").asText();
                }
            }
            if ("DELIVERED".equals(status) || "FAILED".equals(status)) {
                return status;
            }
            Thread.sleep(50);
        }
        return status;
    }

    private static JsonNode findInbox(HttpApiClient client, String subject) throws Exception {
        for (JsonNode item : client.get("/api/v1/mails?folder=INBOX").json().at("/data/items")) {
            if (item.get("subject").asText().equals(subject)) {
                return item;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ tests

    @Test
    void bothInstancesServeTheirOwnDomain() throws Exception {
        assertThat(new HttpApiClient(urlA).get("/api/v1/system/info").json().at("/data/domain").asText())
                .isEqualTo("domain-a.test");
        assertThat(new HttpApiClient(urlB).get("/api/v1/system/info").json().at("/data/domain").asText())
                .isEqualTo("domain-b.test");
    }

    @Test
    void mailFlowsFromAToBAndBackFromBToA() throws Exception {
        String aliceName = user("alice");
        String bobName = user("bob");
        HttpApiClient alice = signedIn(urlA, aliceName);
        HttpApiClient bob = signedIn(urlB, bobName);
        String aliceAddr = aliceName + "@domain-a.test";
        String bobAddr = bobName + "@domain-b.test";

        // A -> B
        String subject = "Cross-domain hello " + aliceName;
        JsonNode sent = send(alice, Map.of("to", List.of(bobAddr), "subject", subject, "body", "Hi Bob"));
        assertThat(awaitDelivery(alice, sent.get("id").asLong(), bobAddr)).isEqualTo("DELIVERED");

        JsonNode received = findInbox(bob, subject);
        assertThat(received).as("bob's inbox on domain-b").isNotNull();
        assertThat(received.at("/from/address").asText()).isEqualTo(aliceAddr);
        assertThat(received.at("/from/displayName").asText()).isEqualTo("User " + aliceName);
        JsonNode detail = bob.get("/api/v1/mails/" + received.get("id").asLong()).json().get("data");
        assertThat(detail.get("body").asText()).isEqualTo("Hi Bob");
        assertThat(detail.get("messageId").asText()).isEqualTo(sent.get("messageId").asText());

        // B -> A (reply direction)
        String reply = "Re: " + subject;
        JsonNode replySent = send(bob, Map.of("to", List.of(aliceAddr), "subject", reply, "body", "Hi Alice"));
        assertThat(awaitDelivery(bob, replySent.get("id").asLong(), aliceAddr)).isEqualTo("DELIVERED");
        assertThat(findInbox(alice, reply)).as("alice's inbox on domain-a").isNotNull();
    }

    @Test
    void groupSendAcrossDomainsWithBccPrivacy() throws Exception {
        String aliceName = user("alice");
        String carolName = user("carol");
        String bobName = user("bob");
        String daveName = user("dave");
        HttpApiClient alice = signedIn(urlA, aliceName);
        HttpApiClient carol = signedIn(urlA, carolName);
        HttpApiClient bob = signedIn(urlB, bobName);
        HttpApiClient dave = signedIn(urlB, daveName);
        String bobAddr = bobName + "@domain-b.test";
        String daveAddr = daveName + "@domain-b.test";
        String carolAddr = carolName + "@domain-a.test";

        String subject = "Group " + aliceName;
        JsonNode sent = send(alice, Map.of(
                "to", List.of(bobAddr), "cc", List.of(carolAddr), "bcc", List.of(daveAddr),
                "subject", subject, "body", "Team update"));
        long sentId = sent.get("id").asLong();
        assertThat(awaitDelivery(alice, sentId, bobAddr)).isEqualTo("DELIVERED");
        assertThat(awaitDelivery(alice, sentId, daveAddr)).isEqualTo("DELIVERED");
        assertThat(findInbox(carol, subject)).as("local cc").isNotNull();

        for (HttpApiClient remote : List.of(bob, dave)) {
            JsonNode item = findInbox(remote, subject);
            assertThat(item).isNotNull();
            JsonNode detail = remote.get("/api/v1/mails/" + item.get("id").asLong()).json().get("data");
            assertThat(detail.get("to").toString()).contains(bobAddr);
            assertThat(detail.get("cc").toString()).contains(carolAddr);
            assertThat(detail.has("bcc")).as("BCC must not leak across domains").isFalse();
        }
        JsonNode bobDetail = bob.get("/api/v1/mails/" + findInbox(bob, subject).get("id").asLong()).json();
        assertThat(bobDetail.toString()).doesNotContain(daveAddr);
    }

    @Test
    void unknownRemoteRecipientFailsAsynchronously() throws Exception {
        String aliceName = user("alice");
        HttpApiClient alice = signedIn(urlA, aliceName);
        String ghost = user("ghost") + "@domain-b.test";

        JsonNode sent = send(alice, Map.of("to", List.of(ghost), "subject", "To nobody", "body", "x"));

        assertThat(awaitDelivery(alice, sent.get("id").asLong(), ghost)).isEqualTo("FAILED");
    }

    @Test
    void domainsAreIsolated() throws Exception {
        String bobName = user("bob");
        signedIn(urlB, bobName);

        // Bob's account exists only on domain-b: domain-a knows nothing about it.
        HttpApiClient onA = new HttpApiClient(urlA);
        assertThat(onA.login(bobName, PASSWORD).status()).isEqualTo(401);
        assertThat(onA.login(bobName + "@domain-b.test", PASSWORD).status()).isEqualTo(401);
    }
}
