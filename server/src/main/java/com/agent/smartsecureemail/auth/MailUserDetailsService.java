package com.agent.smartsecureemail.auth;

import com.agent.smartsecureemail.common.mail.EmailAddress;
import com.agent.smartsecureemail.config.MailDomainProperties;
import com.agent.smartsecureemail.user.User;
import com.agent.smartsecureemail.user.UserRepository;
import com.agent.smartsecureemail.user.UsernamePolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads local users for Spring Security's {@code DaoAuthenticationProvider}. */
@Service
public class MailUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final MailDomainProperties domain;

    public MailUserDetailsService(UserRepository userRepository, MailDomainProperties domain) {
        this.userRepository = userRepository;
        this.domain = domain;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findByUsername(UsernamePolicy.normalize(username))
                // Message is never shown to clients (provider hides it as bad credentials).
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return toPrincipal(user);
    }

    MailUserPrincipal toPrincipal(User user) {
        return new MailUserPrincipal(
                user.getId(),
                user.getUsername(),
                EmailAddress.of(user.getUsername(), domain.name()).value(),
                user.getDisplayName(),
                user.getRole(),
                user.isActive(),
                user.getPasswordHash());
    }
}
