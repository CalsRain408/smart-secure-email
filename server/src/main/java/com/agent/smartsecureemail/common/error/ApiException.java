package com.agent.smartsecureemail.common.error;

import com.agent.smartsecureemail.common.api.ApiError.FieldViolation;
import java.util.List;

/**
 * Business exception carrying a stable {@link ErrorCode}. Thrown from services and
 * translated to an HTTP response by {@link GlobalExceptionHandler}.
 *
 * <p>The message and details must be safe to show to clients: never include secrets,
 * credentials, internal paths or data belonging to other users.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<FieldViolation> details;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), List.of());
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public ApiException(ErrorCode errorCode, String message, List<FieldViolation> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = List.copyOf(details);
    }

    public static ApiException notFound() {
        return new ApiException(ErrorCode.NOT_FOUND);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<FieldViolation> details() {
        return details;
    }
}
