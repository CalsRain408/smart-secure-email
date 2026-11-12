package com.agent.smartsecureemail.auth.dto;

import com.agent.smartsecureemail.user.UsernamePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Password max is 64 because BCrypt only uses the first 72 bytes.
 */
public record RegisterRequest(
        @NotBlank
        @Pattern(regexp = UsernamePolicy.PATTERN,
                message = "must be 3-32 characters: letters, digits, '.', '_' or '-'")
        String username,

        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "must not contain control characters")
        String displayName,

        @NotNull
        @Size(min = 8, max = 64)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "must contain at least one letter and one digit")
        String password,

        @NotNull
        String confirmPassword) {

    @Override
    public String toString() {
        return "RegisterRequest{username=" + username + ", displayName=" + displayName + ", password=***}";
    }
}
