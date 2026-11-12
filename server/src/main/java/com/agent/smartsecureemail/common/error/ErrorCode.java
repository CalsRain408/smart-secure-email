package com.agent.smartsecureemail.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable error codes returned in {@code error.code}. Feature modules add their own
 * codes here as they are implemented; the full catalogue lives in docs/protocol.md.
 */
public enum ErrorCode {

    // Generic
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not allowed"),
    CONFLICT(HttpStatus.CONFLICT, "Resource state conflict"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"),

    // Authentication
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid username or password"),
    AUTH_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "Passwords do not match"),
    AUTH_USERNAME_UNAVAILABLE(HttpStatus.CONFLICT, "Username is not available"),

    // Mail
    MAIL_RECIPIENT_INVALID(HttpStatus.BAD_REQUEST, "One or more recipients cannot receive this mail"),
    MAIL_INVALID_STATE(HttpStatus.CONFLICT, "Operation not allowed in the current mail state"),

    // Federation (server-to-server)
    PEER_UNKNOWN(HttpStatus.UNAUTHORIZED, "Unknown peer or key"),
    PEER_TIMESTAMP_SKEW(HttpStatus.UNAUTHORIZED, "Request timestamp outside the accepted window"),
    PEER_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "Invalid request signature"),
    PEER_REPLAY(HttpStatus.CONFLICT, "Replayed request"),
    ORIGIN_MISMATCH(HttpStatus.BAD_REQUEST, "Message origin does not match the authenticated peer");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
