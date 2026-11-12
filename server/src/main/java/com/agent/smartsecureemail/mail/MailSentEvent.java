package com.agent.smartsecureemail.mail;

import java.time.Instant;
import java.util.Set;

/**
 * Published synchronously inside the send transaction when a message has recipients on
 * federation peers. Listeners must persist their work in the same transaction.
 *
 * @param mailMessageId internal id of the sent message
 * @param peerDomains   peer domains that have recipients of this message
 */
public record MailSentEvent(Long mailMessageId, Set<String> peerDomains, Instant sentAt) {

    public MailSentEvent {
        peerDomains = Set.copyOf(peerDomains);
    }
}
