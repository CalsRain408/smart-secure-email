package com.agent.smartsecureemail.federation.protocol;

import java.util.List;

/**
 * Per-recipient acknowledgement returned by the destination. {@code ACCEPTED} means the
 * mail is durably stored in that recipient's mailbox (sent only after commit).
 */
public record DeliveryReceipt(int protocolVersion, String messageId, List<Result> results) {

    public enum Status {
        ACCEPTED,
        REJECTED
    }

    public record Result(String address, Status status, String reason) {

        public static Result accepted(String address) {
            return new Result(address, Status.ACCEPTED, null);
        }

        public static Result rejected(String address, String reason) {
            return new Result(address, Status.REJECTED, reason);
        }
    }
}
