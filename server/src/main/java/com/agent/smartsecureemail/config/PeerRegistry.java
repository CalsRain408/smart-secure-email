package com.agent.smartsecureemail.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validated, resolved view of the configured federation peers. Fails application
 * startup on any misconfiguration rather than at first delivery.
 */
@Component
public class PeerRegistry {

    private static final Logger log = LoggerFactory.getLogger(PeerRegistry.class);
    static final int MIN_SECRET_BYTES = 32;

    private final Map<String, ResolvedPeer> peers;

    public PeerRegistry(FederationProperties properties, MailDomainProperties domain) {
        Map<String, ResolvedPeer> resolved = new LinkedHashMap<>();
        for (FederationProperties.Peer peer : properties.peers()) {
            String peerDomain = peer.domain().toLowerCase(Locale.ROOT);
            if (domain.isLocalDomain(peerDomain)) {
                throw new IllegalStateException("Federation peer must not be the local domain: " + peerDomain);
            }
            if (resolved.containsKey(peerDomain)) {
                throw new IllegalStateException("Duplicate federation peer: " + peerDomain);
            }
            String scheme = peer.baseUrl().getScheme();
            if (!"https".equals(scheme) && !"http".equals(scheme)) {
                throw new IllegalStateException("Peer base-url must be http(s): " + peerDomain);
            }
            resolved.put(peerDomain, new ResolvedPeer(peerDomain, peer.baseUrl(), peer.keyId(), secretOf(peer)));
        }
        this.peers = Map.copyOf(resolved);
        resolved.values().forEach(p -> {
            log.info("Federation peer configured: domain={} baseUrl={} keyId={}", p.domain(), p.baseUrl(), p.keyId());
            if ("http".equals(p.baseUrl().getScheme())) {
                log.warn("Federation peer {} uses plain HTTP: requests are signed but NOT encrypted. "
                        + "Use the `tls` profile for confidentiality.", p.domain());
            }
        });
    }

    public Optional<ResolvedPeer> find(String domain) {
        return domain == null ? Optional.empty() : Optional.ofNullable(peers.get(domain.toLowerCase(Locale.ROOT)));
    }

    public boolean isPeer(String domain) {
        return find(domain).isPresent();
    }

    public Collection<ResolvedPeer> all() {
        return peers.values();
    }

    private static byte[] secretOf(FederationProperties.Peer peer) {
        boolean hasInline = peer.sharedSecret() != null && !peer.sharedSecret().isBlank();
        boolean hasFile = peer.sharedSecretFile() != null;
        if (hasInline == hasFile) {
            throw new IllegalStateException(
                    "Peer " + peer.domain() + ": set exactly one of shared-secret or shared-secret-file");
        }
        String secret;
        if (hasInline) {
            secret = peer.sharedSecret().trim();
        } else {
            try {
                secret = Files.readString(peer.sharedSecretFile(), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                throw new UncheckedIOException("Peer " + peer.domain() + ": cannot read shared-secret-file "
                        + peer.sharedSecretFile() + " (run scripts/init-dev-secrets.sh for local development)", e);
            }
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "Peer " + peer.domain() + ": shared secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        return bytes;
    }

    /** A peer with its secret loaded. The secret is never included in {@link #toString()}. */
    public record ResolvedPeer(String domain, URI baseUrl, String keyId, byte[] secret) {

        @Override
        public String toString() {
            return "ResolvedPeer{domain=" + domain + ", baseUrl=" + baseUrl + ", keyId=" + keyId + "}";
        }
    }
}
