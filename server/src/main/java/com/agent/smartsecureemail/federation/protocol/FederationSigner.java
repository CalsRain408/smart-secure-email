package com.agent.smartsecureemail.federation.protocol;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Request signing for the federation protocol (docs/protocol.md B.2): HMAC-SHA256 from
 * the JDK over a canonical string. No custom cryptography.
 */
public final class FederationSigner {

    public static final String SCHEME = "SSM-HMAC-SHA256-V1";
    private static final String HMAC = "HmacSHA256";

    private FederationSigner() {
    }

    public static String contentSha256(byte[] body) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String canonical(String method, String path, String origin, String keyId,
                                   String timestamp, String nonce, String contentSha256) {
        return String.join("\n", SCHEME, method.toUpperCase(java.util.Locale.ROOT), path,
                origin, keyId, timestamp, nonce, contentSha256);
    }

    public static String sign(byte[] secret, String canonical) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return Base64.getEncoder().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    /** Constant-time comparison of a presented signature with the expected one. */
    public static boolean verify(byte[] secret, String canonical, String presentedSignature) {
        if (presentedSignature == null) {
            return false;
        }
        byte[] expected = sign(secret, canonical).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, presentedSignature.getBytes(StandardCharsets.US_ASCII));
    }

    /** Constant-time comparison for digests / tokens. */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }
}
