package com.agent.smartsecureemail.federation.outbound;

import com.agent.smartsecureemail.config.FederationProperties;
import com.agent.smartsecureemail.config.MailDomainProperties;
import com.agent.smartsecureemail.config.PeerRegistry.ResolvedPeer;
import com.agent.smartsecureemail.federation.protocol.DeliveryReceipt;
import com.agent.smartsecureemail.federation.protocol.FederationEnvelope;
import com.agent.smartsecureemail.federation.protocol.FederationHeaders;
import com.agent.smartsecureemail.federation.protocol.FederationSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.stereotype.Component;

/**
 * Sends signed envelopes to a peer and classifies the outcome. Performs no retries itself;
 * {@link DeliveryDispatcher} owns the retry policy.
 */
@Component
public class FederationClient {

    private static final Logger log = LoggerFactory.getLogger(FederationClient.class);

    /** Client errors that will not succeed on retry. 409 is excluded: it can be a benign race. */
    private static final Set<Integer> PERMANENT_STATUSES = Set.of(400, 401, 403, 404, 413, 415);

    private final HttpClient http;
    private final ObjectMapper objectMapper;
    private final MailDomainProperties domain;
    private final FederationProperties properties;
    private final Clock clock;

    public FederationClient(ObjectMapper objectMapper, MailDomainProperties domain,
                            FederationProperties properties, Clock clock,
                            ObjectProvider<SslBundles> sslBundles) {
        this.objectMapper = objectMapper;
        this.domain = domain;
        this.properties = properties;
        this.clock = clock;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER);
        if (properties.sslBundle() != null && !properties.sslBundle().isBlank()) {
            builder.sslContext(sslBundles.getObject().getBundle(properties.sslBundle()).createSslContext());
        }
        this.http = builder.build();
    }

    public Outcome deliver(ResolvedPeer peer, FederationEnvelope envelope) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(envelope);
        } catch (IOException e) {
            return new Outcome.Permanent("Cannot serialize envelope");
        }

        String path = FederationHeaders.MESSAGES_PATH;
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String contentSha = FederationSigner.contentSha256(body);
        String signature = FederationSigner.sign(peer.secret(), FederationSigner.canonical(
                "POST", path, domain.name(), peer.keyId(), timestamp, nonce, contentSha));

        HttpRequest request = HttpRequest.newBuilder(URI.create(peer.baseUrl().toString().replaceAll("/+$", "") + path))
                .timeout(properties.requestTimeout())
                .header("Content-Type", "application/json")
                .header(FederationHeaders.ORIGIN, domain.name())
                .header(FederationHeaders.KEY_ID, peer.keyId())
                .header(FederationHeaders.TIMESTAMP, timestamp)
                .header(FederationHeaders.NONCE, nonce)
                .header(FederationHeaders.CONTENT_SHA256, contentSha)
                .header(FederationHeaders.SIGNATURE, signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn("Federation delivery to {} failed: {}", peer.domain(), e.getClass().getSimpleName());
            return new Outcome.Transient("Connection error: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Outcome.Transient("Interrupted");
        }

        int status = response.statusCode();
        if (status == 200) {
            try {
                DeliveryReceipt receipt = objectMapper.readValue(response.body(), DeliveryReceipt.class);
                if (!envelope.messageId().equals(receipt.messageId()) || receipt.results() == null) {
                    return new Outcome.Transient("Receipt does not match the delivered message");
                }
                return new Outcome.Receipt(receipt);
            } catch (IOException e) {
                return new Outcome.Transient("Unreadable receipt");
            }
        }
        String error = "HTTP " + status + errorCodeOf(response.body());
        log.warn("Federation delivery to {} returned {}", peer.domain(), error);
        return PERMANENT_STATUSES.contains(status) ? new Outcome.Permanent(error) : new Outcome.Transient(error);
    }

    private String errorCodeOf(String body) {
        try {
            JsonNode code = objectMapper.readTree(body).at("/error/code");
            return code.isTextual() ? " " + code.asText() : "";
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** Result of one delivery attempt. */
    public sealed interface Outcome {

        record Receipt(DeliveryReceipt receipt) implements Outcome {
        }

        /** Worth retrying later (network, 5xx, 429, 409, malformed receipt). */
        record Transient(String error) implements Outcome {
        }

        /** Will not succeed on retry (authentication/validation rejected by the peer). */
        record Permanent(String error) implements Outcome {
        }
    }
}
