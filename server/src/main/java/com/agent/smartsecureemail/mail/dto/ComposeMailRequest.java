package com.agent.smartsecureemail.mail.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Draft create/update and direct-send payload. Missing lists/strings are treated as empty.
 * The body is plain text; clients must render it escaped.
 */
public record ComposeMailRequest(
        @Size(max = 50) List<@NotNull @Size(max = 320) String> to,
        @Size(max = 50) List<@NotNull @Size(max = 320) String> cc,
        @Size(max = 50) List<@NotNull @Size(max = 320) String> bcc,
        @Size(max = 255)
        @Pattern(regexp = "^[^\\r\\n\\u0000]*$", message = "must be a single line")
        String subject,
        @Size(max = 100_000) String body) {

    public List<String> toOrEmpty() {
        return to == null ? List.of() : to;
    }

    public List<String> ccOrEmpty() {
        return cc == null ? List.of() : cc;
    }

    public List<String> bccOrEmpty() {
        return bcc == null ? List.of() : bcc;
    }

    public String subjectOrEmpty() {
        return subject == null ? "" : subject.strip();
    }

    public String bodyOrEmpty() {
        return body == null ? "" : body;
    }
}
