package com.agent.smartsecureemail.federation.outbound;

import com.agent.smartsecureemail.audit.AuditEventType;
import com.agent.smartsecureemail.audit.AuditOutcome;
import com.agent.smartsecureemail.audit.AuditRecord;
import com.agent.smartsecureemail.audit.AuditService;
import com.agent.smartsecureemail.config.FederationProperties;
import com.agent.smartsecureemail.config.PeerRegistry;
import com.agent.smartsecureemail.config.PeerRegistry.ResolvedPeer;
import com.agent.smartsecureemail.federation.outbound.FederationClient.Outcome;
import com.agent.smartsecureemail.federation.protocol.DeliveryReceipt;
import com.agent.smartsecureemail.federation.protocol.FederationEnvelope;
import com.agent.smartsecureemail.federation.protocol.FederationHeaders;
import com.agent.smartsecureemail.mail.MailMessage;
import com.agent.smartsecureemail.mail.MailMessageRepository;
import com.agent.smartsecureemail.mail.MailRecipient;
import com.agent.smartsecureemail.mail.RecipientType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Delivers outbox entries to peers and applies receipts.
 *
 * <p>Each attempt is: (1) short transaction to build the envelope, (2) HTTP call outside any
 * transaction, (3) short transaction to apply the outcome. A crash between (2) and (3) leaves
 * the entry PENDING; the resend is safe because the receiver is idempotent on messageId.
 * Runs on the single scheduler thread of this (one-process-per-domain) server.
 */
@Component
public class DeliveryDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DeliveryDispatcher.class);

    private final DeliveryOutboxRepository outboxRepository;
    private final MailMessageRepository messageRepository;
    private final PeerRegistry peerRegistry;
    private final FederationClient client;
    private final FederationProperties properties;
    private final TransactionTemplate tx;
    private final AuditService auditService;
    private final Clock clock;

    public DeliveryDispatcher(DeliveryOutboxRepository outboxRepository, MailMessageRepository messageRepository,
                              PeerRegistry peerRegistry, FederationClient client, FederationProperties properties,
                              TransactionTemplate tx, AuditService auditService, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.messageRepository = messageRepository;
        this.peerRegistry = peerRegistry;
        this.client = client;
        this.properties = properties;
        this.tx = tx;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${ssm.federation.dispatch-interval-ms}",
            initialDelayString = "${ssm.federation.dispatch-interval-ms}")
    public void dispatchDue() {
        List<Long> due = tx.execute(status -> outboxRepository
                .findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        DeliveryOutbox.Status.PENDING, clock.instant())
                .stream().map(DeliveryOutbox::getId).toList());
        if (due == null) {
            return;
        }
        for (Long id : due) {
            try {
                dispatch(id);
            } catch (RuntimeException e) {
                // One broken entry must not block the others; it stays PENDING and is retried.
                log.error("Outbox entry {} could not be processed", id, e);
            }
        }
    }

    void dispatch(Long outboxId) {
        Prepared prepared = tx.execute(status -> prepare(outboxId));
        if (prepared == null) {
            return;
        }
        Outcome outcome = prepared.peer() == null
                ? new Outcome.Permanent("Peer is no longer configured")
                : client.deliver(prepared.peer(), prepared.envelope());
        tx.executeWithoutResult(status -> apply(outboxId, outcome));
    }

    private Prepared prepare(Long outboxId) {
        DeliveryOutbox outbox = outboxRepository.findById(outboxId).orElse(null);
        if (outbox == null || outbox.getStatus() != DeliveryOutbox.Status.PENDING) {
            return null;
        }
        MailMessage message = messageRepository.findById(outbox.getMailMessageId()).orElseThrow();
        List<MailRecipient> pending = pendingFor(message, outbox.getPeerDomain());
        if (pending.isEmpty()) {
            outbox.markDone(clock.instant());
            return null;
        }
        ResolvedPeer peer = peerRegistry.find(outbox.getPeerDomain()).orElse(null);
        return new Prepared(peer, envelopeOf(message, pending));
    }

    private void apply(Long outboxId, Outcome outcome) {
        Instant now = clock.instant();
        DeliveryOutbox outbox = outboxRepository.findById(outboxId).orElseThrow();
        MailMessage message = messageRepository.findById(outbox.getMailMessageId()).orElseThrow();
        List<MailRecipient> pending = pendingFor(message, outbox.getPeerDomain());
        int attempt = outbox.recordAttempt(now);

        switch (outcome) {
            case Outcome.Receipt r -> {
                Map<String, DeliveryReceipt.Result> results = r.receipt().results().stream()
                        .filter(res -> res.address() != null)
                        .collect(Collectors.toMap(res -> res.address().toLowerCase(java.util.Locale.ROOT),
                                Function.identity(), (a, b) -> a));
                for (MailRecipient recipient : pending) {
                    DeliveryReceipt.Result result = results.get(recipient.getAddress());
                    if (result == null) {
                        continue; // not acknowledged: stays awaiting, retried below
                    }
                    if (result.status() == DeliveryReceipt.Status.ACCEPTED) {
                        recipient.markDelivered(now);
                    } else {
                        recipient.markFailed("Rejected by " + outbox.getPeerDomain() + ": " + result.reason(), now);
                        auditFailure(message, recipient.getAddress(), result.reason());
                    }
                }
                List<MailRecipient> unacknowledged = pending.stream().filter(MailRecipient::isAwaitingDelivery).toList();
                if (unacknowledged.isEmpty()) {
                    outbox.markDone(now);
                } else {
                    retryOrGiveUp(outbox, message, unacknowledged, attempt, "Peer did not acknowledge recipient", now);
                }
            }
            case Outcome.Permanent p -> {
                pending.forEach(recipient -> {
                    recipient.markFailed(p.error(), now);
                    auditFailure(message, recipient.getAddress(), p.error());
                });
                outbox.markFailed(p.error(), now);
            }
            case Outcome.Transient t -> retryOrGiveUp(outbox, message, pending, attempt, t.error(), now);
        }
        log.info("Outbox {} -> {}: attempt={} status={} ({})", outboxId, outbox.getPeerDomain(), attempt,
                outbox.getStatus(), outcome.getClass().getSimpleName());
    }

    private void retryOrGiveUp(DeliveryOutbox outbox, MailMessage message, List<MailRecipient> recipients,
                               int attempt, String error, Instant now) {
        List<Duration> delays = properties.retryDelays();
        if (attempt > delays.size()) {
            String finalError = "Gave up after " + attempt + " attempts: " + error;
            recipients.forEach(recipient -> {
                recipient.markFailed(finalError, now);
                auditFailure(message, recipient.getAddress(), finalError);
            });
            outbox.markFailed(finalError, now);
            return;
        }
        recipients.forEach(recipient -> recipient.markRetrying(error, now));
        outbox.scheduleRetry(now.plus(delays.get(attempt - 1)), error, now);
    }

    private void auditFailure(MailMessage message, String address, String reason) {
        auditService.record(new AuditRecord(AuditEventType.MAIL_DELIVERY_FAILED, AuditOutcome.FAILURE,
                message.getSenderUserId(), message.getSenderAddress(), "mail_message", message.getMessageId(),
                "recipient=" + address + " reason=" + reason));
    }

    private static List<MailRecipient> pendingFor(MailMessage message, String peerDomain) {
        return message.getRecipients().stream()
                .filter(MailRecipient::isAwaitingDelivery)
                .filter(r -> r.emailAddress().domain().equals(peerDomain))
                .toList();
    }

    /** Envelope for the given recipients; visible headers list all TO/CC across domains (never BCC). */
    static FederationEnvelope envelopeOf(MailMessage message, List<MailRecipient> deliverTo) {
        return new FederationEnvelope(
                FederationHeaders.PROTOCOL_VERSION,
                message.getMessageId(),
                new FederationEnvelope.Party(message.getSenderAddress(), message.getSenderDisplayName()),
                deliverTo.stream()
                        .map(r -> new FederationEnvelope.Recipient(r.getAddress(), r.getRecipientType()))
                        .toList(),
                addresses(message, RecipientType.TO),
                addresses(message, RecipientType.CC),
                message.getSubject(),
                message.getBodyText(),
                message.getInReplyTo(),
                message.getSentAt());
    }

    private static List<String> addresses(MailMessage message, RecipientType type) {
        return message.getRecipients().stream()
                .filter(r -> r.getRecipientType() == type)
                .map(MailRecipient::getAddress)
                .toList();
    }

    private record Prepared(ResolvedPeer peer, FederationEnvelope envelope) {
    }
}
