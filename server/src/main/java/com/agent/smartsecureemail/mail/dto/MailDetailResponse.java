package com.agent.smartsecureemail.mail.dto;

import com.agent.smartsecureemail.mail.MailFolder;
import com.agent.smartsecureemail.mail.MailStatus;
import java.time.Instant;
import java.util.List;

/**
 * Full mail view. {@code bcc} and {@code delivery} are only present when the caller is
 * the author; other recipients never learn who was blind-copied.
 */
public record MailDetailResponse(
        Long id,
        MailFolder folder,
        String messageId,
        MailStatus status,
        AddressView from,
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String subject,
        String body,
        boolean read,
        boolean starred,
        Instant sentAt,
        Instant updatedAt,
        List<RecipientDeliveryView> delivery) {
}
