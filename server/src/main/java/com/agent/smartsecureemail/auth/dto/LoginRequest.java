package com.agent.smartsecureemail.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login payload. {@code username} may be the bare username or the full local address.
 * Password length is capped to bound hashing cost per request.
 */
public record LoginRequest(
        @NotBlank @Size(max = 320) String username,
        @NotBlank @Size(max = 128) String password) {

    @Override
    public String toString() {
        return "LoginRequest{username=" + username + ", password=***}";
    }
}
