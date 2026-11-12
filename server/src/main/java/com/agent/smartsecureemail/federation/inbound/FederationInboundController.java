package com.agent.smartsecureemail.federation.inbound;

import com.agent.smartsecureemail.federation.protocol.DeliveryReceipt;
import com.agent.smartsecureemail.federation.protocol.FederationEnvelope;
import com.agent.smartsecureemail.federation.protocol.FederationHeaders;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-to-server delivery endpoint. Reachable only through the federation filter chain;
 * the peer identity comes from the verified signature, never from the payload.
 */
@RestController
public class FederationInboundController {

    private final FederationInboundService inboundService;

    public FederationInboundController(FederationInboundService inboundService) {
        this.inboundService = inboundService;
    }

    @PostMapping(FederationHeaders.MESSAGES_PATH)
    public DeliveryReceipt receive(PeerAuthentication peer, @Valid @RequestBody FederationEnvelope envelope) {
        return inboundService.receive(peer.peerDomain(), envelope);
    }
}
