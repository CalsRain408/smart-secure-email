package com.agent.smartsecureemail.audit;

/** What to audit. Build with the static factories; all fields except type/outcome are optional. */
public record AuditRecord(
        AuditEventType type,
        AuditOutcome outcome,
        Long actorUserId,
        String actorIdentifier,
        String targetType,
        String targetId,
        String detail) {

    public static AuditRecord success(AuditEventType type, Long actorUserId, String actorIdentifier) {
        return new AuditRecord(type, AuditOutcome.SUCCESS, actorUserId, actorIdentifier, null, null, null);
    }

    public static AuditRecord failure(AuditEventType type, String actorIdentifier, String detail) {
        return new AuditRecord(type, AuditOutcome.FAILURE, null, actorIdentifier, null, null, detail);
    }

    public AuditRecord withTarget(String targetType, String targetId) {
        return new AuditRecord(type, outcome, actorUserId, actorIdentifier, targetType, targetId, detail);
    }

    public AuditRecord withDetail(String detail) {
        return new AuditRecord(type, outcome, actorUserId, actorIdentifier, targetType, targetId, detail);
    }
}
