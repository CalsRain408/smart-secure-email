package com.agent.smartsecureemail.mail;

import com.agent.smartsecureemail.audit.AuditEventType;
import com.agent.smartsecureemail.audit.AuditRecord;
import com.agent.smartsecureemail.audit.AuditService;
import com.agent.smartsecureemail.auth.MailUserPrincipal;
import com.agent.smartsecureemail.common.api.PageResponse;
import com.agent.smartsecureemail.common.error.ApiException;
import com.agent.smartsecureemail.common.error.ErrorCode;
import com.agent.smartsecureemail.common.mail.EmailAddress;
import com.agent.smartsecureemail.config.MailDomainProperties;
import com.agent.smartsecureemail.delivery.RecipientRouter;
import com.agent.smartsecureemail.delivery.RecipientRouter.RoutingResult;
import com.agent.smartsecureemail.mail.dto.ComposeMailRequest;
import com.agent.smartsecureemail.mail.dto.MailDetailResponse;
import com.agent.smartsecureemail.mail.dto.MailFlagsRequest;
import com.agent.smartsecureemail.mail.dto.MailSummaryResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mail use cases for the signed-in user. Every operation resolves the target through
 * {@link #loadOwned}, so a user can never read or modify another user's mailbox items.
 */
@Service
public class MailService {

    private final MailboxItemRepository mailboxItemRepository;
    private final MailMessageRepository mailMessageRepository;
    private final RecipientRouter recipientRouter;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final MailDomainProperties domain;
    private final Clock clock;

    public MailService(MailboxItemRepository mailboxItemRepository,
                       MailMessageRepository mailMessageRepository,
                       RecipientRouter recipientRouter,
                       AuditService auditService,
                       ApplicationEventPublisher events,
                       MailDomainProperties domain,
                       Clock clock) {
        this.mailboxItemRepository = mailboxItemRepository;
        this.mailMessageRepository = mailMessageRepository;
        this.recipientRouter = recipientRouter;
        this.auditService = auditService;
        this.events = events;
        this.domain = domain;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- drafts

    @Transactional
    public MailDetailResponse createDraft(MailUserPrincipal me, ComposeMailRequest request) {
        return MailMapper.toDetail(newDraft(me, request), me.id());
    }

    @Transactional
    public MailDetailResponse updateDraft(MailUserPrincipal me, Long itemId, ComposeMailRequest request) {
        MailboxItem item = loadOwnedDraft(me, itemId);
        applyCompose(item.getMessage(), request);
        mailMessageRepository.flush(); // surface optimistic-lock conflicts inside this call
        return MailMapper.toDetail(item, me.id());
    }

    @Transactional
    public void deleteDraft(MailUserPrincipal me, Long itemId) {
        MailboxItem item = loadOwnedDraft(me, itemId);
        MailMessage message = item.getMessage();
        mailboxItemRepository.delete(item);
        mailMessageRepository.delete(message);
    }

    @Transactional
    public MailDetailResponse sendDraft(MailUserPrincipal me, Long itemId) {
        return send(me, loadOwnedDraft(me, itemId));
    }

    /** Compose and send in one step. Nothing is persisted if validation or routing fails. */
    @Transactional
    public MailDetailResponse sendNew(MailUserPrincipal me, ComposeMailRequest request) {
        return send(me, newDraft(me, request));
    }

    // ---------------------------------------------------------------- reading

    @Transactional(readOnly = true)
    public PageResponse<MailSummaryResponse> list(MailUserPrincipal me, MailFolder folder, int page, int size) {
        Sort sort = folder == MailFolder.DRAFTS
                ? Sort.by(Sort.Order.desc("message.updatedAt"), Sort.Order.desc("id"))
                : Sort.by(Sort.Order.desc("message.sentAt"), Sort.Order.desc("id"));
        return PageResponse.of(
                mailboxItemRepository.findByOwnerUserIdAndFolderAndRecalledFalse(
                        me.id(), folder, PageRequest.of(page, size, sort)),
                item -> MailMapper.toSummary(item, me.id()));
    }

    @Transactional(readOnly = true)
    public MailDetailResponse get(MailUserPrincipal me, Long itemId) {
        return MailMapper.toDetail(loadOwned(me, itemId), me.id());
    }

    @Transactional
    public MailDetailResponse updateFlags(MailUserPrincipal me, Long itemId, MailFlagsRequest request) {
        MailboxItem item = loadOwned(me, itemId);
        item.updateFlags(request.read(), request.starred(), Instant.now(clock));
        return MailMapper.toDetail(item, me.id());
    }

    // ---------------------------------------------------------------- internals

    private MailboxItem newDraft(MailUserPrincipal me, ComposeMailRequest request) {
        Instant now = Instant.now(clock);
        MailMessage message = MailMessage.newLocalDraft(
                newMessageId(),
                EmailAddress.parse(me.address()).orElseThrow(),
                me.displayName(),
                me.id(),
                now);
        applyCompose(message, request);
        mailMessageRepository.save(message);
        return mailboxItemRepository.save(MailboxItem.create(me.id(), message, MailFolder.DRAFTS, now));
    }

    private void applyCompose(MailMessage message, ComposeMailRequest request) {
        List<RecipientSpec> recipients = RecipientNormalizer.normalize(
                request.toOrEmpty(), request.ccOrEmpty(), request.bccOrEmpty());
        message.editDraft(request.subjectOrEmpty(), request.bodyOrEmpty(), recipients, Instant.now(clock));
    }

    /**
     * DRAFT → SENT in one transaction: local recipients get an INBOX item immediately;
     * remote recipients stay PENDING and are queued for federation delivery by a
     * {@link MailSentEvent} listener <em>inside the same transaction</em> (transactional outbox).
     * Either all of this commits, or nothing changes.
     */
    private MailDetailResponse send(MailUserPrincipal me, MailboxItem draftItem) {
        MailMessage message = draftItem.getMessage();
        List<MailRecipient> recipients = message.getRecipients();
        if (recipients.isEmpty()) {
            throw new ApiException(ErrorCode.MAIL_RECIPIENT_INVALID, "At least one recipient is required");
        }

        RoutingResult routing = recipientRouter.route(recipients.stream().map(MailRecipient::emailAddress).toList());
        if (!routing.deliverable()) {
            throw new ApiException(ErrorCode.MAIL_RECIPIENT_INVALID,
                    ErrorCode.MAIL_RECIPIENT_INVALID.defaultMessage(), routing.problems());
        }

        Instant now = Instant.now(clock);
        message.markSent(now);
        draftItem.moveTo(MailFolder.SENT, now);
        for (MailRecipient recipient : recipients) {
            Long recipientUserId = routing.localUserIds().get(recipient.getAddress());
            if (recipientUserId != null) {
                mailboxItemRepository.save(MailboxItem.create(recipientUserId, message, MailFolder.INBOX, now));
                recipient.markDelivered(now);
            }
        }
        if (!routing.remoteByPeer().isEmpty()) {
            mailMessageRepository.flush(); // the outbox row references the message id
            events.publishEvent(new MailSentEvent(message.getId(), routing.remoteByPeer().keySet(), now));
        }
        // Flush before auditing so a concurrent double-send fails here (optimistic lock)
        // and is not recorded as a successful send.
        mailMessageRepository.flush();

        auditService.record(AuditRecord.success(AuditEventType.MAIL_SENT, me.id(), me.getUsername())
                .withTarget("mail_message", message.getMessageId())
                .withDetail("recipients=" + recipients.size()));
        return MailMapper.toDetail(draftItem, me.id());
    }

    private MailboxItem loadOwned(MailUserPrincipal me, Long itemId) {
        // Not-owned and not-existing are indistinguishable to the caller (no id probing).
        return mailboxItemRepository.findByIdAndOwnerUserIdAndRecalledFalse(itemId, me.id())
                .orElseThrow(ApiException::notFound);
    }

    private MailboxItem loadOwnedDraft(MailUserPrincipal me, Long itemId) {
        MailboxItem item = loadOwned(me, itemId);
        if (item.getFolder() != MailFolder.DRAFTS || item.getMessage().getStatus() != MailStatus.DRAFT) {
            throw new ApiException(ErrorCode.MAIL_INVALID_STATE);
        }
        return item;
    }

    private String newMessageId() {
        return UUID.randomUUID() + "@" + domain.name();
    }
}
