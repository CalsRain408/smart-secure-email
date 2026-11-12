package com.agent.smartsecureemail.mail;

import com.agent.smartsecureemail.common.mail.EmailAddress;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A recipient of a message and its delivery state. */
@Entity
@Table(name = "mail_recipients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mail_message_id", nullable = false, updatable = false)
    @Getter(AccessLevel.NONE)
    private MailMessage message;

    @Column(nullable = false, length = 320, updatable = false)
    private String address;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "recipient_type", nullable = false, length = 8)
    private RecipientType recipientType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "delivery_status", nullable = false, length = 24)
    private DeliveryStatus deliveryStatus;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    static MailRecipient pending(MailMessage message, RecipientSpec spec, Instant now) {
        MailRecipient r = new MailRecipient();
        r.message = message;
        r.address = spec.address().value();
        r.recipientType = spec.type();
        r.deliveryStatus = DeliveryStatus.PENDING;
        r.updatedAt = now;
        return r;
    }

    public EmailAddress emailAddress() {
        return EmailAddress.parse(address)
                .orElseThrow(() -> new IllegalStateException("Stored recipient address is invalid"));
    }

    void changeType(RecipientType type, Instant now) {
        if (this.recipientType != type) {
            this.recipientType = type;
            this.updatedAt = now;
        }
    }

    static MailRecipient received(MailMessage message, EmailAddress address, RecipientType type, Instant now) {
        MailRecipient r = new MailRecipient();
        r.message = message;
        r.address = address.value();
        r.recipientType = type;
        r.deliveryStatus = DeliveryStatus.NOT_TRACKED;
        r.updatedAt = now;
        return r;
    }

    /** True while the sender's domain still has to (re)try delivering to this recipient. */
    public boolean isAwaitingDelivery() {
        return deliveryStatus == DeliveryStatus.PENDING || deliveryStatus == DeliveryStatus.RETRYING;
    }

    public void markDelivered(Instant now) {
        requireAwaitingDelivery();
        this.deliveryStatus = DeliveryStatus.DELIVERED;
        this.lastError = null;
        this.updatedAt = now;
    }

    /** Transient failure: delivery will be retried. */
    public void markRetrying(String error, Instant now) {
        requireAwaitingDelivery();
        this.deliveryStatus = DeliveryStatus.RETRYING;
        this.lastError = truncate(error);
        this.updatedAt = now;
    }

    /** Permanent failure: no further attempts. */
    public void markFailed(String error, Instant now) {
        requireAwaitingDelivery();
        this.deliveryStatus = DeliveryStatus.FAILED;
        this.lastError = truncate(error);
        this.updatedAt = now;
    }

    private void requireAwaitingDelivery() {
        if (!isAwaitingDelivery()) {
            throw new IllegalStateException("Recipient is not awaiting delivery: " + deliveryStatus);
        }
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        String cleaned = error.replaceAll("\\p{Cntrl}", " ");
        return cleaned.length() > 512 ? cleaned.substring(0, 512) : cleaned;
    }
}
