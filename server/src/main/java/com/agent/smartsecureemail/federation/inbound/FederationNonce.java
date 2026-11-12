package com.agent.smartsecureemail.federation.inbound;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A nonce already seen from a peer. The (origin, nonce) unique key provides replay protection. */
@Entity
@Table(name = "federation_nonces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class FederationNonce {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "origin_domain", nullable = false, length = 253, updatable = false)
    private String originDomain;

    @Column(nullable = false, length = 64, updatable = false)
    private String nonce;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    FederationNonce(String originDomain, String nonce, Instant expiresAt) {
        this.originDomain = originDomain;
        this.nonce = nonce;
        this.expiresAt = expiresAt;
    }
}
