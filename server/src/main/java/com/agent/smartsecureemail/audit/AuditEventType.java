package com.agent.smartsecureemail.audit;

public enum AuditEventType {
    USER_REGISTERED,
    AUTH_LOGIN_SUCCESS,
    AUTH_LOGIN_FAILURE,
    AUTH_LOGOUT,
    MAIL_SENT,
    MAIL_DELIVERY_FAILED,
    FEDERATION_AUTH_FAILED,
    FEDERATION_MESSAGE_RECEIVED
}
