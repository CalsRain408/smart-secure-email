package com.agent.smartsecureemail.mail;

import com.agent.smartsecureemail.mail.dto.AddressView;
import com.agent.smartsecureemail.mail.dto.MailDetailResponse;
import com.agent.smartsecureemail.mail.dto.MailSummaryResponse;
import com.agent.smartsecureemail.mail.dto.RecipientDeliveryView;
import java.util.List;

/** Entity → DTO mapping. Visibility rules (BCC, delivery status) are applied here. */
final class MailMapper {

    private static final int PREVIEW_LENGTH = 120;

    private MailMapper() {
    }

    static MailSummaryResponse toSummary(MailboxItem item, Long viewerUserId) {
        MailMessage m = item.getMessage();
        boolean authorView = m.isAuthoredBy(viewerUserId) && item.getFolder() != MailFolder.INBOX;
        return new MailSummaryResponse(
                item.getId(),
                item.getFolder(),
                m.getStatus(),
                from(m),
                authorView ? addresses(m, RecipientType.TO) : null,
                m.getSubject(),
                preview(m.getBodyText()),
                item.isRead(),
                item.isStarred(),
                m.getSentAt(),
                m.getUpdatedAt());
    }

    static MailDetailResponse toDetail(MailboxItem item, Long viewerUserId) {
        MailMessage m = item.getMessage();
        boolean author = m.isAuthoredBy(viewerUserId);
        return new MailDetailResponse(
                item.getId(),
                item.getFolder(),
                m.getMessageId(),
                m.getStatus(),
                from(m),
                addresses(m, RecipientType.TO),
                addresses(m, RecipientType.CC),
                author ? addresses(m, RecipientType.BCC) : null,
                m.getSubject(),
                m.getBodyText(),
                item.isRead(),
                item.isStarred(),
                m.getSentAt(),
                m.getUpdatedAt(),
                author ? deliveryViews(m) : null);
    }

    private static AddressView from(MailMessage m) {
        return new AddressView(m.getSenderAddress(), m.getSenderDisplayName());
    }

    private static List<String> addresses(MailMessage m, RecipientType type) {
        return m.getRecipients().stream()
                .filter(r -> r.getRecipientType() == type)
                .map(MailRecipient::getAddress)
                .toList();
    }

    private static List<RecipientDeliveryView> deliveryViews(MailMessage m) {
        return m.getRecipients().stream()
                .map(r -> new RecipientDeliveryView(r.getAddress(), r.getRecipientType(), r.getDeliveryStatus()))
                .toList();
    }

    static String preview(String body) {
        String collapsed = body.strip().replaceAll("\\s+", " ");
        return collapsed.length() <= PREVIEW_LENGTH ? collapsed : collapsed.substring(0, PREVIEW_LENGTH) + "…";
    }
}
