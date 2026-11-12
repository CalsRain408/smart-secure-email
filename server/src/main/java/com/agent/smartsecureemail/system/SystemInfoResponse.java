package com.agent.smartsecureemail.system;

/** Response DTO for {@code GET /api/v1/system/info}. */
public record SystemInfoResponse(String domain, String displayName, String apiVersion, String serverVersion) {
}
