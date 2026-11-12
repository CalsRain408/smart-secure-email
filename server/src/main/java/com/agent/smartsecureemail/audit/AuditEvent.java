package com.agent.smartsecureemail.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only security audit record. Never holds passwords, tokens or mail content. */
@Entity
@Table(name = "security_audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16, updatable = false)
    private AuditOutcome outcome;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "actor_identifier", length = 320, updatable = false)
    private String actorIdentifier;

    @Column(name = "source_ip", length = 64, updatable = false)
    private String sourceIp;

    @Column(name = "target_type", length = 64, updatable = false)
    private String targetType;

    @Column(name = "target_id", updatable = false)
    private String targetId;

    @Column(length = 1024, updatable = false)
    private String detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    static AuditEvent of(AuditRecord record, String sourceIp, Instant now) {
        AuditEvent e = new AuditEvent();
        e.eventType = record.type();
        e.outcome = record.outcome();
        e.actorUserId = record.actorUserId();
        e.actorIdentifier = sanitize(record.actorIdentifier(), 320);
        e.sourceIp = sanitize(sourceIp, 64);
        e.targetType = sanitize(record.targetType(), 64);
        e.targetId = sanitize(record.targetId(), 255);
        e.detail = sanitize(record.detail(), 1024);
        e.occurredAt = now;
        return e;
    }

    /** Strips control characters (log/CSV injection) and truncates to the column size. */
    private static String sanitize(String value, int max) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", "?");
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }
}
