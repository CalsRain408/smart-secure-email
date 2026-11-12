package com.agent.smartsecureemail.mail;

/** Per-recipient delivery state tracked by the sender's domain. */
public enum DeliveryStatus {
    PENDING,
    DELIVERED,
    RETRYING,
    FAILED,
    RECALLED,
    RECALL_FAILED,
    /** Recipient row of a message received from a peer: delivery is tracked by the origin domain. */
    NOT_TRACKED
}
