package com.agent.smartsecureemail.delivery;

import com.agent.smartsecureemail.common.api.ApiError.FieldViolation;
import com.agent.smartsecureemail.common.mail.EmailAddress;
import com.agent.smartsecureemail.config.MailDomainProperties;
import com.agent.smartsecureemail.config.PeerRegistry;
import com.agent.smartsecureemail.user.User;
import com.agent.smartsecureemail.user.UserRepository;
import com.agent.smartsecureemail.user.UserStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides where each recipient address is delivered.
 *
 * <ul>
 *   <li>Local domain + active user → local delivery.</li>
 *   <li>Local domain + no such active user → {@code UNKNOWN_RECIPIENT}.</li>
 *   <li>Configured federation peer → remote delivery via the outbox. Whether the remote
 *       user exists is only known to the peer, so the outcome arrives asynchronously.</li>
 *   <li>Any other domain → {@code DOMAIN_NOT_SUPPORTED}: mail is never accepted for a
 *       domain this server cannot reach.</li>
 * </ul>
 */
@Service
public class RecipientRouter {

    public static final String UNKNOWN_RECIPIENT = "UNKNOWN_RECIPIENT";
    public static final String DOMAIN_NOT_SUPPORTED = "DOMAIN_NOT_SUPPORTED";

    private final UserRepository userRepository;
    private final MailDomainProperties domain;
    private final PeerRegistry peerRegistry;

    public RecipientRouter(UserRepository userRepository, MailDomainProperties domain, PeerRegistry peerRegistry) {
        this.userRepository = userRepository;
        this.domain = domain;
        this.peerRegistry = peerRegistry;
    }

    @Transactional(readOnly = true)
    public RoutingResult route(Collection<EmailAddress> addresses) {
        List<String> localUsernames = addresses.stream()
                .filter(a -> domain.isLocalDomain(a.domain()))
                .map(EmailAddress::localPart)
                .toList();
        Map<String, Long> activeUserIds = localUsernames.isEmpty() ? Map.of()
                : userRepository.findByUsernameInAndStatus(localUsernames, UserStatus.ACTIVE).stream()
                        .collect(Collectors.toMap(User::getUsername, User::getId));

        Map<String, Long> local = new LinkedHashMap<>();
        Map<String, List<String>> remote = new LinkedHashMap<>();
        List<FieldViolation> problems = new ArrayList<>();
        for (EmailAddress address : addresses) {
            if (!domain.isLocalDomain(address.domain())) {
                if (peerRegistry.isPeer(address.domain())) {
                    remote.computeIfAbsent(address.domain(), d -> new ArrayList<>()).add(address.value());
                } else {
                    problems.add(new FieldViolation(address.value(), DOMAIN_NOT_SUPPORTED));
                }
            } else if (activeUserIds.containsKey(address.localPart())) {
                local.put(address.value(), activeUserIds.get(address.localPart()));
            } else {
                problems.add(new FieldViolation(address.value(), UNKNOWN_RECIPIENT));
            }
        }
        return new RoutingResult(local, remote, problems);
    }

    /**
     * @param localUserIds address → local user id for deliverable local recipients
     * @param remoteByPeer peer domain → recipient addresses to deliver via federation
     * @param problems     undeliverable recipients; {@code field} is the address, {@code reason} the code
     */
    public record RoutingResult(Map<String, Long> localUserIds, Map<String, List<String>> remoteByPeer,
                                List<FieldViolation> problems) {

        public boolean deliverable() {
            return problems.isEmpty();
        }
    }
}
