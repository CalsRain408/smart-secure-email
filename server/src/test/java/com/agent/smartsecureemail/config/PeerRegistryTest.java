package com.agent.smartsecureemail.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Peer misconfiguration must stop the server at startup, never at first delivery. */
class PeerRegistryTest {

    private static final String SECRET = "a-sufficiently-long-shared-secret-123";
    private static final MailDomainProperties DOMAIN_A =
            new MailDomainProperties("domain-a", "domain-a.test", "A", Path.of("target/x"));

    private static FederationProperties props(FederationProperties.Peer... peers) {
        return new FederationProperties(List.of(peers), Duration.ofSeconds(300), Duration.ofSeconds(900),
                Duration.ofSeconds(5), List.of(Duration.ofSeconds(1)), 1000, 1024, null);
    }

    private static FederationProperties.Peer peer(String domain, String secret, Path file) {
        return new FederationProperties.Peer(domain, URI.create("http://localhost:1"), "k1", secret, file);
    }

    @Test
    void resolvesInlineAndFileSecrets(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("b.secret");
        Files.writeString(file, SECRET + "\n");

        PeerRegistry registry = new PeerRegistry(
                props(peer("domain-b.test", null, file), peer("domain-c.test", SECRET, null)), DOMAIN_A);

        assertThat(registry.isPeer("DOMAIN-B.TEST")).isTrue();
        assertThat(registry.find("domain-c.test")).get()
                .satisfies(p -> assertThat(new String(p.secret())).isEqualTo(SECRET));
        assertThat(registry.isPeer("domain-z.test")).isFalse();
        assertThat(registry.find("domain-b.test").orElseThrow().toString()).doesNotContain(SECRET);
    }

    @Test
    void rejectsLocalDomainAsPeer() {
        assertThatThrownBy(() -> new PeerRegistry(props(peer("domain-a.test", SECRET, null)), DOMAIN_A))
                .hasMessageContaining("local domain");
    }

    @Test
    void rejectsDuplicatePeers() {
        assertThatThrownBy(() -> new PeerRegistry(
                props(peer("domain-b.test", SECRET, null), peer("domain-b.test", SECRET, null)), DOMAIN_A))
                .hasMessageContaining("Duplicate");
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new PeerRegistry(props(peer("domain-b.test", "too-short", null)), DOMAIN_A))
                .hasMessageContaining("at least");
    }

    @Test
    void requiresExactlyOneSecretSource(@TempDir Path dir) {
        assertThatThrownBy(() -> new PeerRegistry(props(peer("domain-b.test", null, null)), DOMAIN_A))
                .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> new PeerRegistry(
                props(peer("domain-b.test", SECRET, dir.resolve("x"))), DOMAIN_A))
                .hasMessageContaining("exactly one");
    }

    @Test
    void missingSecretFileExplainsHowToFixIt(@TempDir Path dir) {
        assertThatThrownBy(() -> new PeerRegistry(
                props(peer("domain-b.test", null, dir.resolve("missing.secret"))), DOMAIN_A))
                .hasMessageContaining("init-dev-secrets.sh");
    }
}
