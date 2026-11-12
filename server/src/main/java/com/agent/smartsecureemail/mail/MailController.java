package com.agent.smartsecureemail.mail;

import com.agent.smartsecureemail.auth.MailUserPrincipal;
import com.agent.smartsecureemail.common.api.ApiResponse;
import com.agent.smartsecureemail.common.api.PageResponse;
import com.agent.smartsecureemail.mail.dto.ComposeMailRequest;
import com.agent.smartsecureemail.mail.dto.MailDetailResponse;
import com.agent.smartsecureemail.mail.dto.MailFlagsRequest;
import com.agent.smartsecureemail.mail.dto.MailSummaryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Mailbox endpoints. The acting user always comes from the session principal. */
@RestController
@RequestMapping("/api/v1/mails")
public class MailController {

    private final MailService mailService;

    public MailController(MailService mailService) {
        this.mailService = mailService;
    }

    @GetMapping
    public ApiResponse<PageResponse<MailSummaryResponse>> list(
            @AuthenticationPrincipal MailUserPrincipal me,
            @RequestParam MailFolder folder,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(mailService.list(me, folder, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<MailDetailResponse> get(@AuthenticationPrincipal MailUserPrincipal me,
                                               @PathVariable @Positive Long id) {
        return ApiResponse.ok(mailService.get(me, id));
    }

    /** Compose and send immediately. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MailDetailResponse> send(@AuthenticationPrincipal MailUserPrincipal me,
                                                @Valid @RequestBody ComposeMailRequest request) {
        return ApiResponse.ok(mailService.sendNew(me, request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<MailDetailResponse> updateFlags(@AuthenticationPrincipal MailUserPrincipal me,
                                                       @PathVariable @Positive Long id,
                                                       @Valid @RequestBody MailFlagsRequest request) {
        return ApiResponse.ok(mailService.updateFlags(me, id, request));
    }
}
