package com.agent.smartsecureemail.mail.dto;

import jakarta.validation.constraints.AssertTrue;

/** Partial update of the caller's own flags on a mailbox item. */
public record MailFlagsRequest(Boolean read, Boolean starred) {

    @AssertTrue(message = "at least one of read/starred must be provided")
    public boolean isAnyFlagPresent() {
        return read != null || starred != null;
    }
}
