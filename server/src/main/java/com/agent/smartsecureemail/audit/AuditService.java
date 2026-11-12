package com.agent.smartsecureemail.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes security audit events in their own transaction, so a failure event is kept
 * even when the surrounding business transaction rolls back.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repository;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditRecord record) {
        repository.save(AuditEvent.of(record, currentSourceIp(), Instant.now(clock)));
        log.info("audit event={} outcome={} actorUserId={} target={}:{}",
                record.type(), record.outcome(), record.actorUserId(), record.targetType(), record.targetId());
    }

    /**
     * Direct peer address only. X-Forwarded-For is not trusted because no reverse proxy
     * is configured; trusting it would let clients spoof their IP.
     */
    private static String currentSourceIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
