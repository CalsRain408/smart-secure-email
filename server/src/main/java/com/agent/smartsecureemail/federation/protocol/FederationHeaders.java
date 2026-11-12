package com.agent.smartsecureemail.federation.protocol;

/** Header names and paths of federation protocol v1. */
public final class FederationHeaders {

    public static final String ORIGIN = "X-SSM-Origin";
    public static final String KEY_ID = "X-SSM-Key-Id";
    public static final String TIMESTAMP = "X-SSM-Timestamp";
    public static final String NONCE = "X-SSM-Nonce";
    public static final String CONTENT_SHA256 = "X-SSM-Content-SHA256";
    public static final String SIGNATURE = "X-SSM-Signature";

    public static final String MESSAGES_PATH = "/federation/v1/messages";
    public static final int PROTOCOL_VERSION = 1;

    private FederationHeaders() {
    }
}
