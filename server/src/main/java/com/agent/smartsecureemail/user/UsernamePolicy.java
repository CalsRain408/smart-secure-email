package com.agent.smartsecureemail.user;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Username rules. A username is the local part of the user's mail address.
 *
 * <p>Role-like names are reserved so nobody can register an address that impersonates
 * system or administrative mailboxes (a common phishing vector).
 */
public final class UsernamePolicy {

    /** 3-32 chars, letters/digits plus {@code . _ -} inside; validated case-insensitively. */
    public static final String PATTERN = "^[A-Za-z0-9](?:[A-Za-z0-9._-]{1,30}[A-Za-z0-9])$";

    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "root", "system", "postmaster", "hostmaster", "webmaster",
            "abuse", "security", "noreply", "no-reply", "mailer-daemon", "support", "helpdesk",
            "it", "official", "service");

    private UsernamePolicy() {
    }

    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isWellFormed(String normalized) {
        return normalized != null && COMPILED.matcher(normalized).matches();
    }

    public static boolean isReserved(String normalized) {
        return normalized != null && RESERVED.contains(normalized);
    }
}
