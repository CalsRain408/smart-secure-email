package com.agent.smartsecureemail.security;

import com.agent.smartsecureemail.audit.AuditService;
import com.agent.smartsecureemail.auth.MailUserDetailsService;
import com.agent.smartsecureemail.config.FederationProperties;
import com.agent.smartsecureemail.config.PeerRegistry;
import com.agent.smartsecureemail.federation.inbound.FederationAuthenticationFilter;
import com.agent.smartsecureemail.federation.inbound.NonceService;
import com.agent.smartsecureemail.federation.inbound.PeerAuthentication;
import java.time.Clock;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import com.agent.smartsecureemail.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * HTTP security: deny by default, session cookie authentication, CSRF on every
 * state-changing request (including login/register, which prevents login CSRF).
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_GET_ENDPOINTS = {
        "/api/v1/system/info",
        "/api/v1/auth/csrf",
        "/actuator/health",
        "/actuator/health/**",
    };

    private static final String[] PUBLIC_POST_ENDPOINTS = {
        "/api/v1/auth/register",
        "/api/v1/auth/login",
    };

    /**
     * Server-to-server chain for {@code /federation/**}: no session, no CSRF (no cookies are
     * involved), authenticated solely by the HMAC request signature. Evaluated before the
     * browser API chain.
     */
    @Bean
    @Order(1)
    SecurityFilterChain federationSecurityFilterChain(
            HttpSecurity http,
            PeerRegistry peerRegistry,
            NonceService nonceService,
            AuditService auditService,
            FederationProperties federationProperties,
            ObjectMapper objectMapper,
            Clock clock,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .securityMatcher("/federation/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new FederationAuthenticationFilter(peerRegistry, nonceService, auditService,
                        federationProperties, objectMapper, clock), AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(PeerAuthentication.ROLE))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny()));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository) throws Exception {

        http
                // Synchronizer token stored in the session; the default XOR request handler
                // masks the token per response (BREACH mitigation).
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable) // handled by AuthController
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(headers -> headers
                        // API responses are data only: never render or frame them.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny()));

        return http.build();
    }

    /** Requests blocked by the firewall (e.g. "//", ";jsessionid=", encoded traversal) get the JSON envelope. */
    @Bean
    RequestRejectedHandler requestRejectedHandler(ObjectMapper objectMapper) {
        return (request, response, ex) -> SecurityErrorWriter.write(objectMapper, response, ErrorCode.MALFORMED_REQUEST);
    }

    @Bean
    PasswordEncoder passwordEncoder(SecurityProperties properties) {
        return new BCryptPasswordEncoder(properties.passwordHashStrength());
    }

    @Bean
    AuthenticationManager authenticationManager(MailUserDetailsService userDetailsService,
                                                PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Unknown users still incur a hash comparison (built-in timing protection) and
        // surface as BadCredentialsException, so usernames cannot be probed via login.
        ProviderManager manager = new ProviderManager(provider);
        manager.setEraseCredentialsAfterAuthentication(true);
        return manager;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Session-bound synchronizer token (not a double-submit cookie): the token lives only
     * in the server-side session, is rotated on login and dies with the session on logout.
     * The SPA fetches it from {@code GET /api/v1/auth/csrf} and sends it as {@code X-CSRF-TOKEN}.
     */
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    /** On login: issue a new session id (anti session fixation) and rotate the CSRF token. */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }
}
