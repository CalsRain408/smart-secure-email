package com.agent.smartsecureemail.federation.inbound;

import com.agent.smartsecureemail.audit.AuditEventType;
import com.agent.smartsecureemail.audit.AuditOutcome;
import com.agent.smartsecureemail.audit.AuditRecord;
import com.agent.smartsecureemail.audit.AuditService;
import com.agent.smartsecureemail.common.api.ApiError.FieldViolation;
import com.agent.smartsecureemail.common.error.ApiException;
import com.agent.smartsecureemail.common.error.ErrorCode;
import com.agent.smartsecureemail.common.mail.EmailAddress;
import com.agent.smartsecureemail.config.MailDomainProperties;
import com.agent.smartsecureemail.federation.protocol.DeliveryReceipt;
import com.agent.smartsecureemail.federation.protocol.FederationEnvelope;
import com.agent.smartsecureemail.federation.protocol.FederationHeaders;
import com.agent.smartsecureemail.mail.MailFolder;
import com.agent.smartsecureemail.mail.MailMessage;
import com.agent.smartsecureemail.mail.MailMessageRepository;
import com.agent.smartsecureemail.mail.MailOrigin;
import com.agent.smartsecureemail.mail.MailboxItem;
import com.agent.smartsecureemail.mail.MailboxItemRepository;
import com.agent.smartsecureemail.mail.RecipientType;
import com.agent.smartsecureemail.user.User;
import com.agent.smartsecureemail.user.UserRepository;
import com.agent.smartsecureemail.user.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts envelopes from an authenticated peer. Idempotent on {@code messageId}: a resend
 * never duplicates the message or inbox items and yields the same per-recipient results.
 */
@Service
public class FederationInboundService {

    public static final String RECIPIENT_NOT_FOUND = "RECIPIENT_NOT_FOUND";
    public static final String RECIPIENT_DOMAIN_MISMATCH = "RECIPIENT_DOMAIN_MISMATCH";
    public static final String INVALID_ADDRESS = "INVALID_ADDRESS";

    /** {@code <opaque id>@<origin domain>}; the id part is what the origin generated (a UUID for us). */
    private static final Pattern MESSAGE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,128}@([a-z0-9.-]{1,253})$");

    private final MailMessageRepository messageRepository;
    private final MailboxItemRepository mailboxItemRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final MailDomainProperties domain;
    private final Clock clock;

    public FederationInboundService(MailMessageRepository messageRepository,
                                    MailboxItemRepository mailboxItemRepository,
                                    UserRepository userRepository, AuditService auditService,
                                    MailDomainProperties domain, Clock clock) {
        this.messageRepository = messageRepository;
        this.mailboxItemRepository = mailboxItemRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.domain = domain;
        this.clock = clock;
    }

    /** Runs in one transaction; the controller returns the receipt only after it commits. */
    @Transactional
    public DeliveryReceipt receive(String origin, FederationEnvelope envelope) {
        Instant now = clock.instant();
        EmailAddress from = parseOrInvalid(envelope.from().address(), "from.address");
        Matcher id = MESSAGE_ID.matcher(envelope.messageId());
        if (!id.matches()) {
            throw invalid("messageId", "must be <id>@<origin-domain>");
        }
        // A peer may only speak for its own domain: no spoofed senders, no foreign message ids.
        if (!from.domain().equals(origin) || !id.group(1).equals(origin)) {
            throw new ApiException(ErrorCode.ORIGIN_MISMATCH);
        }

        MailMessage message = messageRepository.findByMessageId(envelope.messageId())
                .map(existing -> requireSameMessage(existing, from))
                .orElseGet(() -> storeNew(envelope, from, now));

        List<DeliveryReceipt.Result> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int accepted = 0;
        for (FederationEnvelope.Recipient recipient : envelope.recipients()) {
            Optional<EmailAddress> address = EmailAddress.parse(recipient.address());
            if (address.isEmpty()) {
                results.add(DeliveryReceipt.Result.rejected(recipient.address(), INVALID_ADDRESS));
                continue;
            }
            EmailAddress to = address.get();
            if (!seen.add(to.value())) {
                continue;
            }
            if (!domain.isLocalDomain(to.domain())) {
                results.add(DeliveryReceipt.Result.rejected(to.value(), RECIPIENT_DOMAIN_MISMATCH));
                continue;
            }
            Optional<User> user = userRepository.findByUsername(to.localPart()).filter(u -> u.getStatus() == UserStatus.ACTIVE);
            if (user.isEmpty()) {
                results.add(DeliveryReceipt.Result.rejected(to.value(), RECIPIENT_NOT_FOUND));
                continue;
            }
            message.addReceivedRecipient(to, recipient.type(), now);
            if (!mailboxItemRepository.existsByOwnerUserIdAndMessage_IdAndFolder(
                    user.get().getId(), message.getId(), MailFolder.INBOX)) {
                mailboxItemRepository.save(MailboxItem.create(user.get().getId(), message, MailFolder.INBOX, now));
            }
            results.add(DeliveryReceipt.Result.accepted(to.value()));
            accepted++;
        }

        auditService.record(new AuditRecord(AuditEventType.FEDERATION_MESSAGE_RECEIVED, AuditOutcome.SUCCESS,
                null, origin, "mail_message", envelope.messageId(),
                "accepted=" + accepted + " rejected=" + (results.size() - accepted)));
        return new DeliveryReceipt(FederationHeaders.PROTOCOL_VERSION, envelope.messageId(), results);
    }

    private MailMessage storeNew(FederationEnvelope envelope, EmailAddress from, Instant now) {
        MailMessage message = MailMessage.receivedFromPeer(
                envelope.messageId(), from, envelope.from().displayName(),
                envelope.subject().strip(), envelope.bodyText(), envelope.inReplyTo(),
                envelope.sentAt(), now);
        addVisible(message, envelope.visibleToOrEmpty(), RecipientType.TO, "visibleTo", now);
        addVisible(message, envelope.visibleCcOrEmpty(), RecipientType.CC, "visibleCc", now);
        return messageRepository.saveAndFlush(message);
    }

    private static void addVisible(MailMessage message, List<String> addresses, RecipientType type,
                                   String field, Instant now) {
        for (int i = 0; i < addresses.size(); i++) {
            message.addReceivedRecipient(parseOrInvalid(addresses.get(i), field + "[" + i + "]"), type, now);
        }
    }

    /** A resend must refer to the same message; anything else is a protocol violation. */
    private static MailMessage requireSameMessage(MailMessage existing, EmailAddress from) {
        if (existing.getOrigin() != MailOrigin.REMOTE || !existing.getSenderAddress().equals(from.value())) {
            throw new ApiException(ErrorCode.CONFLICT, "messageId already used for a different message");
        }
        return existing;
    }

    private static EmailAddress parseOrInvalid(String raw, String field) {
        return EmailAddress.parse(raw).orElseThrow(() -> invalid(field, "invalid mail address"));
    }

    private static ApiException invalid(String field, String reason) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(),
                List.of(new FieldViolation(field, reason)));
    }
}
