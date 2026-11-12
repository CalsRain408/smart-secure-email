package com.agent.smartsecureemail.common.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressTest {

    @Test
    void parsesAndLowercases() {
        EmailAddress address = EmailAddress.parse("  Alice.Smith@Domain-A.TEST ").orElseThrow();

        assertThat(address.localPart()).isEqualTo("alice.smith");
        assertThat(address.domain()).isEqualTo("domain-a.test");
        assertThat(address.value()).isEqualTo("alice.smith@domain-a.test");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "alice",                          // no domain
        "@domain-a.test",                 // no local part
        "alice@",                         // empty domain
        "alice@@domain-a.test",           // double @
        "alice@b@domain-a.test",          // two @
        "alice@localhost",                // no dot in domain
        "\"alice\"@domain-a.test",        // quoted local part
        "Alice <alice@domain-a.test>",    // display name syntax
        "alice@domain-a.test\r\nBcc: x@y.z", // header injection attempt
        ".alice@domain-a.test",           // leading dot
        "alice@-domain.test",             // label starts with hyphen
        "../etc/passwd@domain-a.test",    // path characters
    })
    void rejectsMalformedInput(String raw) {
        assertThat(EmailAddress.parse(raw)).isEmpty();
    }

    @Test
    void rejectsOverlongAddress() {
        String raw = "a".repeat(64) + "@" + "b".repeat(250) + ".test";
        assertThat(EmailAddress.parse(raw)).isEmpty();
    }
}
