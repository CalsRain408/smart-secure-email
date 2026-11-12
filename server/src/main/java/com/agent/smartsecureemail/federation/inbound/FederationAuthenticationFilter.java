package com.agent.smartsecureemail.federation.inbound;

import com.agent.smartsecureemail.audit.AuditRecord;
import com.agent.smartsecureemail.audit.AuditEventType;
import com.agent.smartsecureemail.audit.AuditService;
import com.agent.smartsecureemail.common.api.ApiError;
import com.agent.smartsecureemail.common.api.ApiResponse;
import com.agent.smartsecureemail.common.error.ErrorCode;
import com.agent.smartsecureemail.config.FederationProperties;
import com.agent.smartsecureemail.config.PeerRegistry;
import com.agent.smartsecureemail.config.PeerRegistry.ResolvedPeer;
import com.agent.smartsecureemail.federation.protocol.FederationHeaders;
import com.agent.smartsecureemail.federation.protocol.FederationSigner;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates peer servers on {@code /federation/**} (docs/protocol.md B.2.2):
 * known peer + key id → timestamp window → body digest → HMAC signature (constant time)
 * → nonce not seen before. Only then is the request passed on, with the verified body.
 *
 * <p>Not a Spring bean on purpose: it must only run inside the federation filter chain.
 */
public class FederationAuthenticationFilter extends OncePerRequestFilter {

    private static final Pattern NONCE = Pattern.compile("^[A-Za-z0-9-]{16,64}$");
    private static final Pattern TIMESTAMP = Pattern.compile("^\\d{1,12}$");

    private final PeerRegistry peerRegistry;
    private final NonceService nonceService;
    private final AuditService auditService;
    private final FederationProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FederationAuthenticationFilter(PeerRegistry peerRegistry, NonceService nonceService,
                                          AuditService auditService, FederationProperties properties,
                                          ObjectMapper objectMapper, Clock clock) {
        this.peerRegistry = peerRegistry;
        this.nonceService = nonceService;
        this.auditService = auditService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (request.getContentLengthLong() > properties.maxEnvelopeBytes()) {
            reject(response, ErrorCode.PAYLOAD_TOO_LARGE, request, "content-length over limit");
            return;
        }
        byte[] body = readBounded(request.getInputStream(), properties.maxEnvelopeBytes());
        if (body == null) {
            reject(response, ErrorCode.PAYLOAD_TOO_LARGE, request, "body over limit");
            return;
        }

        String origin = header(request, FederationHeaders.ORIGIN);
        String keyId = header(request, FederationHeaders.KEY_ID);
        String timestamp = header(request, FederationHeaders.TIMESTAMP);
        String nonce = header(request, FederationHeaders.NONCE);
        String contentSha = header(request, FederationHeaders.CONTENT_SHA256);
        String signature = header(request, FederationHeaders.SIGNATURE);

        Optional<ResolvedPeer> peer = peerRegistry.find(origin);
        if (peer.isEmpty() || !peer.get().keyId().equals(keyId)) {
            reject(response, ErrorCode.PEER_UNKNOWN, request, "unknown peer or key id");
            return;
        }
        if (timestamp == null || !TIMESTAMP.matcher(timestamp).matches()
                || Math.abs(clock.instant().getEpochSecond() - Long.parseLong(timestamp))
                        > properties.clockSkew().toSeconds()) {
            reject(response, ErrorCode.PEER_TIMESTAMP_SKEW, request, "timestamp outside window");
            return;
        }
        if (nonce == null || !NONCE.matcher(nonce).matches()
                || !FederationSigner.constantTimeEquals(FederationSigner.contentSha256(body), contentSha)) {
            reject(response, ErrorCode.PEER_SIGNATURE_INVALID, request, "malformed nonce or body digest mismatch");
            return;
        }
        String canonical = FederationSigner.canonical(request.getMethod(), request.getRequestURI(),
                peer.get().domain(), keyId, timestamp, nonce, contentSha);
        if (!FederationSigner.verify(peer.get().secret(), canonical, signature)) {
            reject(response, ErrorCode.PEER_SIGNATURE_INVALID, request, "signature mismatch");
            return;
        }
        // Checked last so unauthenticated garbage cannot fill the nonce table.
        if (!nonceService.register(peer.get().domain(), nonce)) {
            reject(response, ErrorCode.PEER_REPLAY, request, "nonce replay");
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new PeerAuthentication(peer.get().domain()));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(new CachedBodyRequest(request, body), response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** Reads at most {@code limit} bytes; returns null if the body is larger. */
    private static byte[] readBounded(InputStream in, int limit) throws IOException {
        byte[] data = in.readNBytes(limit + 1);
        return data.length > limit ? null : data;
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? null : value.trim();
    }

    private void reject(HttpServletResponse response, ErrorCode code, HttpServletRequest request, String reason)
            throws IOException {
        String origin = header(request, FederationHeaders.ORIGIN);
        auditService.record(AuditRecord.failure(AuditEventType.FEDERATION_AUTH_FAILED,
                origin == null ? "(none)" : origin, code.name() + ": " + reason));
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.failure(new ApiError(code.name(), code.defaultMessage())));
    }
}
