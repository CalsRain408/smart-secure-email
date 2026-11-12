package com.agent.smartsecureemail.auth.dto;

/** CSRF token for the current session and the header the SPA must send it in. */
public record CsrfTokenResponse(String headerName, String token) {
}
