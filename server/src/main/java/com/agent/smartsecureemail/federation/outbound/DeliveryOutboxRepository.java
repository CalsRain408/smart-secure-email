package com.agent.smartsecureemail.federation.outbound;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryOutboxRepository extends JpaRepository<DeliveryOutbox, Long> {

    List<DeliveryOutbox> findTop20ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            DeliveryOutbox.Status status, Instant now);

    Optional<DeliveryOutbox> findByMailMessageIdAndPeerDomain(Long mailMessageId, String peerDomain);
}
