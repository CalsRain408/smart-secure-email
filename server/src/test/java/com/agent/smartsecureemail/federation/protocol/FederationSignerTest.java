package com.agent.smartsecureemail.federation.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FederationSignerTest {

    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final byte[] BODY = "{\"messageId\":\"x@domain-a.test\"}".getBytes(StandardCharsets.UTF_8);

    private static String canonical(String path, String origin, String ts, String nonce, byte[] body) {
        return FederationSigner.canonical("POST", path, origin, "k1", ts, nonce, FederationSigner.contentSha256(body));
    }

    @Test
    void validSignatureVerifies() {
        String c = canonical("/federation/v1/messages", "domain-a.test", "1762930387", "n-0000000000000001", BODY);
        assertThat(FederationSigner.verify(SECRET, c, FederationSigner.sign(SECRET, c))).isTrue();
    }

    @Test
    void anyChangeInvalidatesTheSignature() {
        String original = canonical("/federation/v1/messages", "domain-a.test", "1762930387", "n-0000000000000001", BODY);
        String signature = FederationSigner.sign(SECRET, original);

        assertThat(FederationSigner.verify(SECRET,
                canonical("/federation/v1/recalls", "domain-a.test", "1762930387", "n-0000000000000001", BODY),
                signature)).as("path").isFalse();
        assertThat(FederationSigner.verify(SECRET,
                canonical("/federation/v1/messages", "domain-c.test", "1762930387", "n-0000000000000001", BODY),
                signature)).as("origin").isFalse();
        assertThat(FederationSigner.verify(SECRET,
                canonical("/federation/v1/messages", "domain-a.test", "1762930388", "n-0000000000000001", BODY),
                signature)).as("timestamp").isFalse();
        assertThat(FederationSigner.verify(SECRET,
                canonical("/federation/v1/messages", "domain-a.test", "1762930387", "n-0000000000000002", BODY),
                signature)).as("nonce").isFalse();
        assertThat(FederationSigner.verify(SECRET,
                canonical("/federation/v1/messages", "domain-a.test", "1762930387", "n-0000000000000001",
                        "{\"messageId\":\"y@domain-a.test\"}".getBytes(StandardCharsets.UTF_8)),
                signature)).as("body").isFalse();
        assertThat(FederationSigner.verify("another-secret-another-secret-xx".getBytes(StandardCharsets.UTF_8),
                original, signature)).as("key").isFalse();
        assertThat(FederationSigner.verify(SECRET, original, null)).as("missing").isFalse();
    }

    @Test
    void canonicalFieldsAreNewlineSeparatedAndMethodIsUppercased() {
        assertThat(FederationSigner.canonical("post", "/p", "o", "k", "1", "n", "h"))
                .isEqualTo("SSM-HMAC-SHA256-V1\nPOST\n/p\no\nk\n1\nn\nh");
    }
}
