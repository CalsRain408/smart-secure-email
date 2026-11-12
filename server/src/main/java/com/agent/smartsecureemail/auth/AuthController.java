package com.agent.smartsecureemail.auth;

import com.agent.smartsecureemail.audit.AuditEventType;
import com.agent.smartsecureemail.audit.AuditRecord;
import com.agent.smartsecureemail.audit.AuditService;
import com.agent.smartsecureemail.auth.dto.CsrfTokenResponse;
import com.agent.smartsecureemail.auth.dto.CurrentUserResponse;
import com.agent.smartsecureemail.auth.dto.LoginRequest;
import com.agent.smartsecureemail.auth.dto.RegisterRequest;
import com.agent.smartsecureemail.common.api.ApiResponse;
import com.agent.smartsecureemail.common.error.ApiException;
import com.agent.smartsecureemail.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session-based authentication for the browser client.
 *
 * <p>Login: authenticate → change session id (anti-fixation) and rotate CSRF token →
 * store SecurityContext in the HttpSession. The session cookie is HttpOnly/SameSite.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final AuditService auditService;
    private final String sessionCookieName;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AuthController(AuthService authService,
                          AuthenticationManager authenticationManager,
                          SessionAuthenticationStrategy sessionAuthenticationStrategy,
                          SecurityContextRepository securityContextRepository,
                          CsrfTokenRepository csrfTokenRepository,
                          AuditService auditService,
                          @Value("${server.servlet.session.cookie.name}") String sessionCookieName) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.auditService = auditService;
        this.sessionCookieName = sessionCookieName;
    }

    /**
     * Returns the session-bound CSRF token (masked per response). The SPA calls this on start-up
     * and again after login/logout, keeps it in memory and sends it as the X-CSRF-TOKEN header.
     */
    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenResponse> csrf(CsrfToken token) {
        return ApiResponse.ok(new CsrfTokenResponse(token.getHeaderName(), token.getToken()));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CurrentUserResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            return ApiResponse.ok(authService.register(request));
        } catch (DataIntegrityViolationException concurrentRegistration) {
            // Lost a race on the unique username; the service transaction is already rolled back.
            throw new ApiException(ErrorCode.AUTH_USERNAME_UNAVAILABLE);
        }
    }

    @PostMapping("/login")
    public ApiResponse<CurrentUserResponse> login(@Valid @RequestBody LoginRequest request,
                                                  HttpServletRequest httpRequest,
                                                  HttpServletResponse httpResponse) {
        String username = authService.resolveLoginUsername(request.username())
                .orElseThrow(() -> loginFailed(request.username(), "malformed or foreign identifier"));

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, request.password()));
        } catch (AuthenticationException e) {
            // Same response for unknown user, wrong password and disabled account.
            throw loginFailed(username, e.getClass().getSimpleName());
        }

        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        issueFreshCsrfToken(httpRequest);

        MailUserPrincipal principal = (MailUserPrincipal) authentication.getPrincipal();
        auditService.record(AuditRecord.success(
                AuditEventType.AUTH_LOGIN_SUCCESS, principal.id(), principal.getUsername()));
        return ApiResponse.ok(CurrentUserResponse.of(principal));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal MailUserPrincipal principal,
                                    Authentication authentication,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        new CsrfLogoutHandler(csrfTokenRepository).logout(request, response, authentication);
        new CookieClearingLogoutHandler(sessionCookieName).logout(request, response, authentication);
        if (principal != null) {
            auditService.record(AuditRecord.success(AuditEventType.AUTH_LOGOUT, principal.id(), principal.getUsername()));
        }
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(@AuthenticationPrincipal MailUserPrincipal principal) {
        if (principal == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return ApiResponse.ok(CurrentUserResponse.of(principal));
    }

    private ApiException loginFailed(String identifier, String reason) {
        auditService.record(AuditRecord.failure(AuditEventType.AUTH_LOGIN_FAILURE, identifier, reason));
        return new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    /**
     * Materialize the rotated (deferred) token so it is stored in the new session now.
     * The client must fetch it via GET /api/v1/auth/csrf; the pre-login token is no longer valid.
     */
    private static void issueFreshCsrfToken(HttpServletRequest request) {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
    }
}
