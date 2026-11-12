package com.agent.smartsecureemail.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Error body. {@code code} is a stable machine-readable identifier (see docs/protocol.md);
 * {@code message} is safe for display and never contains secrets or stack traces.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, List<FieldViolation> details) {

    public ApiError(String code, String message) {
        this(code, message, List.of());
    }

    /** A single input validation failure. */
    public record FieldViolation(String field, String reason) {
    }
}
