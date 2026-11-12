package com.agent.smartsecureemail.federation.outbound;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Pending federation delivery of one message to one peer domain (transactional outbox). */
@Entity
@Table(name = "delivery_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryOutbox {

    public enum Status {
        PENDING,
        DONE,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mail_message_id", nullable = false, updatable = false)
    private Long mailMessageId;

    @Column(name = "peer_domain", nullable = false, length = 253, updatable = false)
    private String peerDomain;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    static DeliveryOutbox create(Long mailMessageId, String peerDomain, Instant now) {
        DeliveryOutbox o = new DeliveryOutbox();
        o.mailMessageId = mailMessageId;
        o.peerDomain = peerDomain;
        o.status = Status.PENDING;
        o.nextAttemptAt = now;
        o.createdAt = now;
        o.updatedAt = now;
        return o;
    }

    /** Counts an attempt; returns the new attempt number (1-based). */
    int recordAttempt(Instant now) {
        attempts++;
        updatedAt = now;
        return attempts;
    }

    void scheduleRetry(Instant next, String error, Instant now) {
        nextAttemptAt = next;
        lastError = error;
        updatedAt = now;
    }

    void markDone(Instant now) {
        status = Status.DONE;
        lastError = null;
        updatedAt = now;
    }

    void markFailed(String error, Instant now) {
        status = Status.FAILED;
        lastError = error;
        updatedAt = now;
    }
}
