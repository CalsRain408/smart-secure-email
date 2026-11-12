package com.agent.smartsecureemail.security;

import com.agent.smartsecureemail.common.api.ApiError;
import com.agent.smartsecureemail.common.api.ApiResponse;
import com.agent.smartsecureemail.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/** Writes the standard error envelope from security filters (outside MVC exception handling). */
final class SecurityErrorWriter {

    private SecurityErrorWriter() {
    }

    static void write(ObjectMapper objectMapper, HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.failure(new ApiError(code.name(), code.defaultMessage())));
    }
}
