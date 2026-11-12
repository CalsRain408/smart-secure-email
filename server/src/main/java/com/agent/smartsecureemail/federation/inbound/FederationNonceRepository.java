package com.agent.smartsecureemail.federation.inbound;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface FederationNonceRepository extends JpaRepository<FederationNonce, Long> {

    @Modifying
    @Query("delete from FederationNonce n where n.expiresAt < :now")
    int deleteExpired(Instant now);
}
