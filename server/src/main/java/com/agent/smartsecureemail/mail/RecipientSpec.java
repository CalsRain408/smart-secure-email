package com.agent.smartsecureemail.mail;

import com.agent.smartsecureemail.common.mail.EmailAddress;

/** A validated, normalized recipient requested by the author. */
public record RecipientSpec(EmailAddress address, RecipientType type) {
}
