package com.agent.smartsecureemail.mail;

import com.agent.smartsecureemail.common.error.ApiException;
import com.agent.smartsecureemail.common.error.ErrorCode;
import com.agent.smartsecureemail.common.mail.EmailAddress;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A logical message known to this domain. State transitions are enforced here:
 * only a DRAFT can be edited or sent.
 */
@Entity
@Table(name = "mail_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Globally stable id {@code <uuid>@<origin-domain>}; idempotency key for federation. */
    @Column(name = "message_id", nullable = false, updatable = false)
    private String messageId;

    @Column(name = "sender_address", nullable = false, length = 320, updatable = false)
    private String senderAddress;

    @Column(name = "sender_display_name", length = 128)
    private String senderDisplayName;

    /** Set only when the author is a local user. */
    @Column(name = "sender_user_id", updatable = false)
    private Long senderUserId;

    @Column(nullable = false, length = 998)
    private String subject;

    @Column(name = "body_text", nullable = false, length = 100_000)
    private String bodyText;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16, updatable = false)
    private MailOrigin origin;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private MailStatus status;

    @Column(name = "in_reply_to")
    private String inReplyTo;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Getter(AccessLevel.NONE)
    private List<MailRecipient> recipients = new ArrayList<>();

    public static MailMessage newLocalDraft(String messageId, EmailAddress sender, String senderDisplayName,
                                            Long senderUserId, Instant now) {
        MailMessage m = new MailMessage();
        m.messageId = messageId;
        m.senderAddress = sender.value();
        m.senderDisplayName = senderDisplayName;
        m.senderUserId = senderUserId;
        m.subject = "";
        m.bodyText = "";
        m.origin = MailOrigin.LOCAL;
        m.status = MailStatus.DRAFT;
        m.createdAt = now;
        m.updatedAt = now;
        return m;
    }

    /**
     * A message received from a federation peer. It is immutable here (status SENT);
     * recipient rows are display-only ({@link DeliveryStatus#NOT_TRACKED}).
     */
    public static MailMessage receivedFromPeer(String messageId, EmailAddress sender, String senderDisplayName,
                                               String subject, String bodyText, String inReplyTo,
                                               Instant sentAt, Instant now) {
        MailMessage m = new MailMessage();
        m.messageId = messageId;
        m.senderAddress = sender.value();
        m.senderDisplayName = senderDisplayName;
        m.subject = subject;
        m.bodyText = bodyText;
        m.inReplyTo = inReplyTo;
        m.origin = MailOrigin.REMOTE;
        m.status = MailStatus.SENT;
        m.sentAt = sentAt;
        m.createdAt = now;
        m.updatedAt = now;
        return m;
    }

    /** Adds a display recipient to a received message; no-op if the address is already listed. */
    public void addReceivedRecipient(EmailAddress address, RecipientType type, Instant now) {
        if (origin != MailOrigin.REMOTE) {
            throw new IllegalStateException("Only received messages take display recipients");
        }
        boolean present = recipients.stream().anyMatch(r -> r.getAddress().equals(address.value()));
        if (!present) {
            recipients.add(MailRecipient.received(this, address, type, now));
        }
    }

    public List<MailRecipient> getRecipients() {
        return Collections.unmodifiableList(recipients);
    }

    public boolean isAuthoredBy(Long userId) {
        return senderUserId != null && senderUserId.equals(userId);
    }

    public void editDraft(String subject, String bodyText, List<RecipientSpec> desired, Instant now) {
        requireStatus(MailStatus.DRAFT);
        this.subject = subject;
        this.bodyText = bodyText;
        syncRecipients(desired, now);
        this.updatedAt = now;
    }

    public void markSent(Instant now) {
        requireStatus(MailStatus.DRAFT);
        this.status = MailStatus.SENT;
        this.sentAt = now;
        this.updatedAt = now;
    }

    private void requireStatus(MailStatus expected) {
        if (status != expected) {
            throw new ApiException(ErrorCode.MAIL_INVALID_STATE);
        }
    }

    /**
     * Updates recipients in place instead of clear-and-reinsert: Hibernate flushes inserts
     * before deletes, which would violate the (message, address) unique constraint.
     */
    private void syncRecipients(List<RecipientSpec> desired, Instant now) {
        Map<String, RecipientType> wanted = new HashMap<>();
        desired.forEach(spec -> wanted.put(spec.address().value(), spec.type()));

        recipients.removeIf(existing -> !wanted.containsKey(existing.getAddress()));
        Set<String> kept = recipients.stream().map(MailRecipient::getAddress).collect(Collectors.toSet());
        recipients.forEach(existing -> existing.changeType(wanted.get(existing.getAddress()), now));

        for (RecipientSpec spec : desired) {
            if (!kept.contains(spec.address().value())) {
                recipients.add(MailRecipient.pending(this, spec, now));
            }
        }
    }
}
