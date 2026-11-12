package com.agent.smartsecureemail.mail.dto;

import com.agent.smartsecureemail.mail.MailFolder;
import com.agent.smartsecureemail.mail.MailStatus;
import java.time.Instant;
import java.util.List;

/** List row. {@code to} is only filled for the author's own copies (SENT/DRAFTS). */
public record MailSummaryResponse(
        Long id,
        MailFolder folder,
        MailStatus status,
        AddressView from,
        List<String> to,
        String subject,
        String preview,
        boolean read,
        boolean starred,
        Instant sentAt,
        Instant updatedAt) {
}
