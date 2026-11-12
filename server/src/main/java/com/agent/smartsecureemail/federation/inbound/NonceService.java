package com.agent.smartsecureemail.federation.inbound;

import com.agent.smartsecureemail.config.FederationProperties;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Remembers nonces for {@code nonceTtl} (longer than the accepted timestamp window), so a
 * captured request cannot be replayed: inside the window its nonce is known, outside the
 * window its timestamp is rejected. Database-backed; moves to Redis in Phase 5.
 */
@Service
public class NonceService {

    private static final Logger log = LoggerFactory.getLogger(NonceService.class);

    private final FederationNonceRepository repository;
    private final FederationProperties properties;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    public NonceService(FederationNonceRepository repository, FederationProperties properties,
                        PlatformTransactionManager transactionManager, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /**
     * Returns true if the nonce was not seen before for this origin (and records it).
     * The insert runs in its own transaction and the duplicate-key exception is caught
     * <em>outside</em> it; catching inside a transactional method would leave that
     * transaction marked rollback-only.
     */
    public boolean register(String origin, String nonce) {
        try {
            requiresNew.executeWithoutResult(status -> repository.saveAndFlush(
                    new FederationNonce(origin, nonce, clock.instant().plus(properties.nonceTtl()))));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }

    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteExpired(Instant.now(clock));
        if (removed > 0) {
            log.debug("Purged {} expired federation nonces", removed);
        }
    }
}
