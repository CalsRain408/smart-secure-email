package com.agent.smartsecureemail.federation.outbound;

import com.agent.smartsecureemail.mail.MailSentEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes outbox rows in the <em>same</em> transaction as the send. If the send rolls back,
 * nothing is queued; if it commits, delivery is guaranteed to be attempted.
 */
@Component
class OutboxEnqueuer {

    private final DeliveryOutboxRepository repository;

    OutboxEnqueuer(DeliveryOutboxRepository repository) {
        this.repository = repository;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onMailSent(MailSentEvent event) {
        for (String peer : event.peerDomains()) {
            repository.save(DeliveryOutbox.create(event.mailMessageId(), peer, event.sentAt()));
        }
    }
}
