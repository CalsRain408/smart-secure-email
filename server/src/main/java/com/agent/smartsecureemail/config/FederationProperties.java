package com.agent.smartsecureemail.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Inter-server (federation) settings. See docs/protocol.md Part B.
 *
 * @param peers              statically configured trusted peer domains; anything else is rejected
 * @param clockSkew          accepted |now - X-SSM-Timestamp|
 * @param nonceTtl           how long a seen nonce is remembered (must exceed 2 x clockSkew)
 * @param requestTimeout     outbound HTTP timeout
 * @param retryDelays        backoff schedule; attempts = retryDelays.size() + 1
 * @param dispatchIntervalMs outbox polling interval
 * @param maxEnvelopeBytes   inbound request body limit
 * @param sslBundle          optional Spring SSL bundle (trust store) for outbound HTTPS
 */
@Validated
@ConfigurationProperties(prefix = "ssm.federation")
public record FederationProperties(
        @Valid List<Peer> peers,
        @NotNull Duration clockSkew,
        @NotNull Duration nonceTtl,
        @NotNull Duration requestTimeout,
        @NotEmpty List<Duration> retryDelays,
        @Positive long dispatchIntervalMs,
        @Positive int maxEnvelopeBytes,
        String sslBundle) {

    public FederationProperties {
        peers = peers == null ? List.of() : List.copyOf(peers);
    }

    /**
     * A trusted peer. Exactly one of {@code sharedSecret} (from an environment variable)
     * or {@code sharedSecretFile} must be set. Secrets never appear in {@link #toString()}.
     */
    public record Peer(
            @NotBlank @Pattern(regexp = MailDomainProperties.DOMAIN_PATTERN) String domain,
            @NotNull URI baseUrl,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{1,64}$") String keyId,
            String sharedSecret,
            Path sharedSecretFile) {

        @Override
        public String toString() {
            return "Peer{domain=" + domain + ", baseUrl=" + baseUrl + ", keyId=" + keyId + ", secret=***}";
        }
    }
}
