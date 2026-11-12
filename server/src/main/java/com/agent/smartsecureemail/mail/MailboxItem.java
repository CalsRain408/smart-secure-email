package com.agent.smartsecureemail.mail;

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
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One user's view of a message. Its id is the mail id exposed to clients, and every
 * lookup is scoped by {@code ownerUserId}, which is how ownership is enforced.
 */
@Entity
@Table(name = "mailbox_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailboxItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private Long ownerUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mail_message_id", nullable = false, updatable = false)
    private MailMessage message;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private MailFolder folder;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "is_starred", nullable = false)
    private boolean starred;

    @Column(nullable = false)
    private boolean recalled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public static MailboxItem create(Long ownerUserId, MailMessage message, MailFolder folder, Instant now) {
        MailboxItem item = new MailboxItem();
        item.ownerUserId = ownerUserId;
        item.message = message;
        item.folder = folder;
        // The author's own copies (draft/sent) are read by definition.
        item.read = folder != MailFolder.INBOX;
        item.createdAt = now;
        item.updatedAt = now;
        return item;
    }

    public void moveTo(MailFolder target, Instant now) {
        this.folder = target;
        this.updatedAt = now;
    }

    public void updateFlags(Boolean read, Boolean starred, Instant now) {
        if (read != null) {
            this.read = read;
        }
        if (starred != null) {
            this.starred = starred;
        }
        this.updatedAt = now;
    }
}
