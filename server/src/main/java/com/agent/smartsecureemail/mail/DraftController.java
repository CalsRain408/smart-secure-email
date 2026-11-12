package com.agent.smartsecureemail.mail;

import com.agent.smartsecureemail.auth.MailUserPrincipal;
import com.agent.smartsecureemail.common.api.ApiResponse;
import com.agent.smartsecureemail.mail.dto.ComposeMailRequest;
import com.agent.smartsecureemail.mail.dto.MailDetailResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Draft lifecycle. Draft ids are the caller's mailbox item ids in the DRAFTS folder. */
@RestController
@RequestMapping("/api/v1/drafts")
public class DraftController {

    private final MailService mailService;

    public DraftController(MailService mailService) {
        this.mailService = mailService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MailDetailResponse> create(@AuthenticationPrincipal MailUserPrincipal me,
                                                  @Valid @RequestBody ComposeMailRequest request) {
        return ApiResponse.ok(mailService.createDraft(me, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<MailDetailResponse> update(@AuthenticationPrincipal MailUserPrincipal me,
                                                  @PathVariable @Positive Long id,
                                                  @Valid @RequestBody ComposeMailRequest request) {
        return ApiResponse.ok(mailService.updateDraft(me, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal MailUserPrincipal me,
                                    @PathVariable @Positive Long id) {
        mailService.deleteDraft(me, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/send")
    public ApiResponse<MailDetailResponse> send(@AuthenticationPrincipal MailUserPrincipal me,
                                                @PathVariable @Positive Long id) {
        return ApiResponse.ok(mailService.sendDraft(me, id));
    }
}
