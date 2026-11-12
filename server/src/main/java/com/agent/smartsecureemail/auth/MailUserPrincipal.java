package com.agent.smartsecureemail.auth;

import com.agent.smartsecureemail.user.UserRole;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated user stored in the session. This is the <strong>only</strong> source
 * of the acting user's identity; request payloads never carry user ids.
 * The password hash is erased right after authentication.
 */
public final class MailUserPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private final String address;
    private final String displayName;
    private final UserRole role;
    private final boolean enabled;
    private String passwordHash;

    public MailUserPrincipal(Long id, String username, String address, String displayName,
                             UserRole role, boolean enabled, String passwordHash) {
        this.id = id;
        this.username = username;
        this.address = address;
        this.displayName = displayName;
        this.role = role;
        this.enabled = enabled;
        this.passwordHash = passwordHash;
    }

    public Long id() {
        return id;
    }

    public String address() {
        return address;
    }

    public String displayName() {
        return displayName;
    }

    public UserRole role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    @Override
    public String toString() {
        return "MailUserPrincipal{id=" + id + ", username=" + username + ", role=" + role + "}";
    }
}
