package com.agent.smartsecureemail.auth.dto;

import com.agent.smartsecureemail.auth.MailUserPrincipal;
import com.agent.smartsecureemail.user.UserRole;

/** Public view of the signed-in user. Internal ids and hashes are never exposed. */
public record CurrentUserResponse(String username, String address, String displayName, UserRole role) {

    public static CurrentUserResponse of(MailUserPrincipal principal) {
        return new CurrentUserResponse(
                principal.getUsername(), principal.address(), principal.displayName(), principal.role());
    }
}
