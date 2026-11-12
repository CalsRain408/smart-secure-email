package com.agent.smartsecureemail.system;

import com.agent.smartsecureemail.common.api.ApiResponse;
import com.agent.smartsecureemail.config.MailDomainProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, non-sensitive instance metadata. Lets clients and operators confirm which
 * mail domain a server process is serving. Exposes no user data, paths or secrets.
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private static final String API_VERSION = "v1";

    private final MailDomainProperties domain;
    private final ObjectProvider<BuildProperties> buildProperties;

    public SystemController(MailDomainProperties domain, ObjectProvider<BuildProperties> buildProperties) {
        this.domain = domain;
        this.buildProperties = buildProperties;
    }

    @GetMapping("/info")
    public ApiResponse<SystemInfoResponse> info() {
        BuildProperties build = buildProperties.getIfAvailable();
        String serverVersion = build != null ? build.getVersion() : "dev";
        return ApiResponse.ok(new SystemInfoResponse(
                domain.name(), domain.displayName(), API_VERSION, serverVersion));
    }
}
