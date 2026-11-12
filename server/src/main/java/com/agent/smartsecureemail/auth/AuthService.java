package com.agent.smartsecureemail.auth;

import com.agent.smartsecureemail.audit.AuditEventType;
import com.agent.smartsecureemail.audit.AuditRecord;
import com.agent.smartsecureemail.audit.AuditService;
import com.agent.smartsecureemail.auth.dto.CurrentUserResponse;
import com.agent.smartsecureemail.auth.dto.RegisterRequest;
import com.agent.smartsecureemail.common.api.ApiError.FieldViolation;
import com.agent.smartsecureemail.common.error.ApiException;
import com.agent.smartsecureemail.common.error.ErrorCode;
import com.agent.smartsecureemail.common.mail.EmailAddress;
import com.agent.smartsecureemail.config.MailDomainProperties;
import com.agent.smartsecureemail.organization.OrganizationService;
import com.agent.smartsecureemail.user.User;
import com.agent.smartsecureemail.user.UserRepository;
import com.agent.smartsecureemail.user.UsernamePolicy;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration and login identifier rules. Session handling lives in {@link AuthController}. */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationService organizationService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final MailDomainProperties domain;
    private final Clock clock;

    public AuthService(UserRepository userRepository, OrganizationService organizationService,
                       PasswordEncoder passwordEncoder, AuditService auditService,
                       MailDomainProperties domain, Clock clock) {
        this.userRepository = userRepository;
        this.organizationService = organizationService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.domain = domain;
        this.clock = clock;
    }

    /** Creates a MEMBER account in the default organization. Does not sign the user in. */
    @Transactional
    public CurrentUserResponse register(RegisterRequest request) {
        String username = UsernamePolicy.normalize(request.username());

        if (!constantTimeEquals(request.password(), request.confirmPassword())) {
            throw new ApiException(ErrorCode.AUTH_PASSWORD_MISMATCH);
        }
        if (request.password().toLowerCase(Locale.ROOT).contains(username)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(),
                    List.of(new FieldViolation("password", "must not contain the username")));
        }
        // Reserved and taken names share one response so reserved names are not distinguishable.
        if (UsernamePolicy.isReserved(username) || userRepository.existsByUsername(username)) {
            throw new ApiException(ErrorCode.AUTH_USERNAME_UNAVAILABLE);
        }

        // A concurrent registration of the same name surfaces as DataIntegrityViolationException
        // from this flush; AuthController maps it after this transaction has rolled back.
        User user = userRepository.saveAndFlush(User.register(
                organizationService.defaultOrganization().getId(),
                username,
                request.displayName().trim(),
                passwordEncoder.encode(request.password()),
                Instant.now(clock)));

        auditService.record(AuditRecord.success(AuditEventType.USER_REGISTERED, user.getId(), username));
        String address = EmailAddress.of(username, domain.name()).value();
        return new CurrentUserResponse(username, address, user.getDisplayName(), user.getRole());
    }

    /**
     * Maps a login identifier to a local username. Accepts {@code alice} or
     * {@code alice@<this domain>}; anything else (including other domains) yields empty.
     */
    public Optional<String> resolveLoginUsername(String identifier) {
        if (identifier == null) {
            return Optional.empty();
        }
        if (identifier.contains("@")) {
            return EmailAddress.parse(identifier)
                    .filter(address -> domain.isLocalDomain(address.domain()))
                    .map(EmailAddress::localPart);
        }
        String normalized = UsernamePolicy.normalize(identifier);
        return UsernamePolicy.isWellFormed(normalized) ? Optional.of(normalized) : Optional.empty();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
