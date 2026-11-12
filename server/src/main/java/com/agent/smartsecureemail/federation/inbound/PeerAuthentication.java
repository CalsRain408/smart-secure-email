package com.agent.smartsecureemail.federation.inbound;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** An authenticated federation peer. The principal is the peer's domain name. */
public final class PeerAuthentication extends AbstractAuthenticationToken {

    public static final String ROLE = "FEDERATION_PEER";

    private final String peerDomain;

    PeerAuthentication(String peerDomain) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + ROLE)));
        this.peerDomain = peerDomain;
        setAuthenticated(true);
    }

    public String peerDomain() {
        return peerDomain;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return peerDomain;
    }
}
