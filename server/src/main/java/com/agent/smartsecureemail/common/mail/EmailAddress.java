package com.agent.smartsecureemail.common.mail;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normalized (lowercase) mail address {@code local@domain} as used inside this system.
 *
 * <p>This is intentionally stricter than RFC 5322: no quoted local parts, comments or
 * display names. All accounts in the system follow the same username rules, so a
 * strict grammar removes a whole class of parsing ambiguities.
 */
public record EmailAddress(String localPart, String domain) {

    public static final int MAX_LENGTH = 320;

    private static final Pattern LOCAL_PART =
            Pattern.compile("^[a-z0-9](?:[a-z0-9._-]{0,62}[a-z0-9])?$");
    private static final Pattern DOMAIN =
            Pattern.compile("^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z][a-z0-9-]{0,62}$");

    public EmailAddress {
        if (localPart == null || !LOCAL_PART.matcher(localPart).matches()) {
            throw new IllegalArgumentException("Invalid local part");
        }
        if (domain == null || !DOMAIN.matcher(domain).matches()) {
            throw new IllegalArgumentException("Invalid domain");
        }
    }

    /** Parses and lowercases {@code raw}; returns empty for anything malformed. */
    public static Optional<EmailAddress> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        int at = value.indexOf('@');
        if (at <= 0 || at != value.lastIndexOf('@') || at == value.length() - 1) {
            return Optional.empty();
        }
        try {
            return Optional.of(new EmailAddress(value.substring(0, at), value.substring(at + 1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static EmailAddress of(String localPart, String domain) {
        return new EmailAddress(localPart.toLowerCase(Locale.ROOT), domain.toLowerCase(Locale.ROOT));
    }

    public String value() {
        return localPart + "@" + domain;
    }

    @Override
    public String toString() {
        return value();
    }
}
